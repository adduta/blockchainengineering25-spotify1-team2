package nl.tudelft.trustchain.musicdao.core.ipv8

import android.annotation.SuppressLint
import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.search.KeywordSearchMessage
import com.frostwire.jlibtorrent.Sha1Hash
import kotlinx.coroutines.DelicateCoroutinesApi
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetRequestMessage
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetResponseMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import java.time.Instant
import java.time.temporal.ChronoUnit

@Suppress("DEPRECATION")
class MusicCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    private var cacheDatabase: CacheDatabase,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>() // All recent swarm health data that
    // has been received from peers

    // Channel to handle magnet responses
    private val magnetResponseChannel = Channel<MagnetResponseMessage>(UNLIMITED)

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val cacheDatabase: CacheDatabase,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<MusicCommunity>(MusicCommunity::class.java) {
        override fun create(): MusicCommunity {
            return MusicCommunity(settings, database, cacheDatabase, crawler)
        }
    }

    init {
        messageHandlers[MessageId.KEYWORD_SEARCH_MESSAGE] = ::onKeywordSearch
        messageHandlers[MessageId.SWARM_HEALTH_MESSAGE] = ::onSwarmHealth
        messageHandlers[MessageId.MAGNET_REQUEST_MESSAGE] = ::onMagnetRequest
        messageHandlers[MessageId.MAGNET_RESPONSE_MESSAGE] = ::onMagnetResponse
    }

    fun performRemoteKeywordSearch(
        keyword: String,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        val maxPeersToAsk = 20 // This is a magic number, tweak during/after experiments
        var count = 0
        for ((index, peer) in getPeers().withIndex()) {
            if (index >= maxPeersToAsk) break
            val packet =
                serializePacket(
                    MessageId.KEYWORD_SEARCH_MESSAGE,
                    KeywordSearchMessage(originPublicKey, ttl, keyword)
                )
            send(peer, packet)
            count += 1
        }
        return count
    }

    /**
     * When a peer asks for some music content with keyword, browse through my local collection of
     * blocks to find whether I have something. If I do, send the corresponding block directly back
     * to the original asker. If I don't, I will ask my peers to find it
     */
    private fun onKeywordSearch(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(KeywordSearchMessage)
        val keyword = payload.keyword.toLowerCase(Locale.ROOT)
        val block = localKeywordSearch(keyword)
        if (block != null) sendBlock(block, peer)
        if (block == null) {
            if (!payload.checkTTL()) return
            performRemoteKeywordSearch(keyword, payload.ttl, payload.originPublicKey)
        }
        Log.i("KeywordSearch", peer.mid + ": " + payload.keyword)
    }

    /**
     * Peers in the MusicCommunity iteratively gossip a few swarm health statistics of the torrents
     * they are currently tracking
     */
    private fun onSwarmHealth(packet: Packet) {
        val (_, swarmHealth) = packet.getAuthPayload(SwarmHealth)
        swarmHealthMap[Sha1Hash(swarmHealth.infoHash)] = swarmHealth
    }

    /**
     * Send a SwarmHealth message to a random peer
     */
    fun sendSwarmHealthMessage(swarmHealth: SwarmHealth): Boolean {
        val peer = pickRandomPeer() ?: return false
        send(peer, serializePacket(MessageId.SWARM_HEALTH_MESSAGE, swarmHealth))
        return true
    }

    /**
     * Filter local databse to find a release block that matches a certain title or artist, using
     * keyword search
     */
    @SuppressLint("NewApi")
    fun localKeywordSearch(keyword: String): TrustChainBlock? {
        database.getBlocksWithType("publish_release").forEach {
            val transaction = it.transaction
            val title = transaction["title"]?.toString()?.toLowerCase(Locale.ROOT)
            val artists = transaction["artists"]?.toString()?.toLowerCase(Locale.ROOT)
            if (title != null && title.contains(keyword)) {
                return it
            } else if (artists != null && artists.contains(keyword)) {
                return it
            }
        }
        return null
    }

    private fun pickRandomPeer(): Peer? {
        val peers = getPeers()
        if (peers.isEmpty()) return null
        return peers.random()
    }

    fun publicKeyHex(): String {
        return this.myPeer.publicKey.keyToBin().toHex()
    }

    fun publicKeyStringToPublicKey(publicKey: String): PublicKey {
        return defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
    }

    fun publicKeyStringToByteArray(publicKey: String): ByteArray {
        return publicKeyStringToPublicKey(publicKey).keyToBin()
    }

    fun setCacheDatabase(cacheDatabase: CacheDatabase) {
        this.cacheDatabase = cacheDatabase
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onMagnetRequest(packet: Packet) {
        val (peer, request) = packet.getAuthPayload(MagnetRequestMessage)
        Log.d("MusicCommunity", "Received magnet link request for release ${request.releaseId} from peer ${peer.mid}")

        // Launch a coroutine to handle the request asynchronously
        GlobalScope.launch {
            try {
                // Get the release from our local cache database
                val albumEntity = cacheDatabase.dao.get(request.releaseId)
                if (albumEntity != null) {
                    Log.d("MusicCommunity", "Found release ${request.releaseId} in local database with magnet: ${albumEntity.magnet}")

                    // PERFORM ACCESS CONTROL CHECKS
                    val hasAccess = checkUserAccess(request.originPublicKey, albumEntity)

                    if (hasAccess && albumEntity.magnet.isNotEmpty() && albumEntity.magnet != "access_restricted") {
                        // If user has access and we have the magnet link, send it back
                        val response = MagnetResponseMessage(
                            releaseId = request.releaseId,
                            magnetLink = albumEntity.magnet
                        )

                        val responsePacket = serializePacket(
                            MessageId.MAGNET_RESPONSE_MESSAGE,
                            response
                        )

                        send(peer, responsePacket)
                        Log.d("MusicCommunity", "Sent magnet link for release ${request.releaseId} to peer ${peer.mid}")
                    } else {
                        Log.d("MusicCommunity", "Access denied for release ${request.releaseId} to peer ${peer.mid} or magnet link unavailable")
                    }
                } else {
                    Log.d("MusicCommunity", "Release ${request.releaseId} not found in local database")
                }
            } catch (e: Exception) {
                Log.e("MusicCommunity", "Error handling magnet request: ${e.message}")
            }
        }
    }

    /**
     * Check if the requesting user has access to the given album
     * This is the server-side access control that cannot be bypassed
     */
    private fun checkUserAccess(userPublicKey: ByteArray, albumEntity: AlbumEntity): Boolean {
        try {
            Log.d("MusicCommunity", "Checking access for user ${userPublicKey.toHex()} to album ${albumEntity.id}")
            Log.d("MusicCommunity", "Album isExclusive: ${albumEntity.isExclusive}, releaseDate: ${albumEntity.releaseDate}")

            val isUltimate = isUltimateUser(userPublicKey)
            val isPro = isProUser(userPublicKey)
            val isPastDelay = isReleasePastDelayPeriod(albumEntity.releaseDate)

            Log.d("MusicCommunity", "User access check - isUltimate: $isUltimate, isPro: $isPro, isPastDelay: $isPastDelay")

            if (albumEntity.isExclusive) {
                // Exclusive content only for Ultimate users
                if (isUltimate) {
                    Log.d("MusicCommunity", "Granting access to exclusive release ${albumEntity.id} for Ultimate user")
                    return true
                } else {
                    Log.d("MusicCommunity", "Denying access to exclusive release ${albumEntity.id} - user is not Ultimate tier")
                    return false
                }
            } else if (isPro || isPastDelay) {
                Log.d("MusicCommunity", "Release ${albumEntity.id} is past delay period or user is Pro/Ultimate, granting access")
                return true
            } else {
                Log.d("MusicCommunity", "Denying access to release ${albumEntity.id} - user is not Pro tier and release is not past delay")
                return false
            }
        } catch (e: Exception) {
            Log.e("MusicCommunity", "Error checking user access: ${e.message}")
            return false
        }
    }

    /**
     * Check if a release is past the delay period (7 days)
     */
    private fun isReleasePastDelayPeriod(releaseDate: String): Boolean {
        try {
            val releaseInstant = Instant.parse(releaseDate)
            val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
            return releaseInstant.isBefore(sevenDaysAgo)
        } catch (e: Exception) {
            Log.e("MusicCommunity", "Error parsing release date: ${e.message}")
            return false
        }
    }

    private fun onMagnetResponse(packet: Packet) {
        val (peer, response) = packet.getAuthPayload(MagnetResponseMessage)
        Log.d("MusicCommunity", "For release ${response.releaseId}, magnet link ${response.magnetLink} was received from peer ${peer.mid}")
        // Persist the received magnet link in the database.
        val infoHash: String = TorrentEngine.magnetToInfoHash(response.magnetLink) ?: ""
        cacheDatabase.dao.updateReleaseMagnet(response.releaseId, response.magnetLink, infoHash)
    }

    // Function to get a magnet response from the channel with timeout
    suspend fun getMagnetResponse(timeoutMillis: Long = 5000): MagnetResponseMessage? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMillis) {
                Log.d("MusicCommunity", "Waiting for magnet response with timeout $timeoutMillis ms")
                magnetResponseChannel.receive()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Function to request a magnet link from peers
    fun requestMagnetLink(
        releaseId: String,
        ttl: UInt = 20u
    ): Int {
        val maxPeersToAsk = 20 // This is a magic number, tweak during/after experiments
        var count = 0
        Log.d("MusicCommunity", "Requesting magnet link for release $releaseId with TTL=$ttl")
        for ((index, peer) in getPeers().withIndex()) {
            if (index >= maxPeersToAsk) break
            val packet =
                serializePacket(
                    MessageId.MAGNET_REQUEST_MESSAGE,
                    MagnetRequestMessage(myPeer.publicKey.keyToBin(), ttl, releaseId)
                )
            send(peer, packet)
            count += 1
        }
        Log.d("MusicCommunity", "Sent magnet link request to $count peers with TTL=$ttl")
        return count
    }

    fun isProUser(userPublicKey: ByteArray): Boolean {
        Log.d("MusicCommunity", "Checking if user ${userPublicKey.toHex()} is Pro")
        val userTierBlocks = getBlocksForUser(userPublicKey)
        Log.d("MusicCommunity", "Found ${userTierBlocks.size} user tier blocks for user ${userPublicKey.toHex()}")

        // If there are no tier blocks, user is not Pro
        if (userTierBlocks.isEmpty()) {
            Log.d("MusicCommunity", "No user tier blocks found for user ${userPublicKey.toHex()}")
            return false
        }

        // Get the most recent valid tier block
        val currentTime = System.currentTimeMillis()
        Log.d("MusicCommunity", "Current time: $currentTime")
        val validTierBlock =
            userTierBlocks
                .filter { it.validFrom <= currentTime && (it.validUntil == null || it.validUntil > currentTime) }
                .maxByOrNull { it.validFrom }

        // If there is no valid tier block, user is not Pro
        if (validTierBlock == null) {
            Log.d("MusicCommunity", "No valid tier block found for user ${userPublicKey.toHex()}")
            return false
        }

        Log.d("MusicCommunity", "Valid tier block found for user ${userPublicKey.toHex()}: tier=${validTierBlock.tier}, validFrom=${validTierBlock.validFrom}, validUntil=${validTierBlock.validUntil}")

        // Both PRO and ULTIMATE users have access to PRO features
        val isPro = validTierBlock.tier == "PRO" || validTierBlock.tier == "ULTIMATE"
        Log.d("MusicCommunity", "User ${userPublicKey.toHex()} isPro: $isPro")
        return isPro
    }

    fun isUltimateUser(userPublicKey: ByteArray): Boolean {
        val userTierBlocks = getBlocksForUser(userPublicKey)

        // If there are no tier blocks, user is not Ultimate
        if (userTierBlocks.isEmpty()) {
            return false
        }

        // Get the most recent valid tier block
        val currentTime = System.currentTimeMillis()
        val validTierBlock =
            userTierBlocks
                .filter { it.validFrom <= currentTime && (it.validUntil == null || it.validUntil > currentTime) }
                .maxByOrNull { it.validFrom }

        // If there is no valid tier block, user is not Ultimate
        if (validTierBlock == null) {
            return false
        }

        Log.d(
            "UserTierVerifier",
            "isUltimateUser: Valid tier block found: ${validTierBlock.tier}," +
                " valid from ${validTierBlock.validFrom} to ${validTierBlock.validUntil}"
        )

        return validTierBlock.tier == "ULTIMATE"
    }

    fun getBlocksForUser(userPublicKey: ByteArray): List<UserTierBlock> {
        Log.d("MusicCommunity", "Getting blocks for user ${userPublicKey.toHex()}")
        val allUserTierBlocks = database.getBlocksWithType(UserTierBlock.BLOCK_TYPE)
        Log.d("MusicCommunity", "Found ${allUserTierBlocks.size} total user tier blocks in database")

        val userBlocks = allUserTierBlocks
            .filter { it.publicKey.contentEquals(userPublicKey) }
            .map { toBlock(it) }

        Log.d("MusicCommunity", "Found ${userBlocks.size} user tier blocks for user ${userPublicKey.toHex()}")
        return userBlocks
    }

    fun toBlock(block: TrustChainBlock): UserTierBlock {
        @Suppress("UNCHECKED_CAST")
        val transaction = block.transaction as Map<String, Any>
        return UserTierBlock(
            userId = transaction["userId"] as String,
            tier = transaction["tier"] as String,
            validFrom = (transaction["validFrom"] as Number).toLong(),
            validUntil = (transaction["validUntil"] as? Number)?.toLong()
        )
    }

    object MessageId {
        const val KEYWORD_SEARCH_MESSAGE = 10
        const val SWARM_HEALTH_MESSAGE = 11
        const val MAGNET_REQUEST_MESSAGE = 14
        const val MAGNET_RESPONSE_MESSAGE = 15
    }
}
