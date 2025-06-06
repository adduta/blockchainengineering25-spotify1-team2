package nl.tudelft.trustchain.musicdao.core.ipv8

import android.annotation.SuppressLint
import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.search.KeywordSearchMessage
import com.frostwire.jlibtorrent.Sha1Hash
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
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetRequestMessage
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetResponseMessage

@Suppress("DEPRECATION")
class MusicCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"
    var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>() // All recent swarm health data that
    // has been received from peers

    @Inject
    lateinit var albumRepository: AlbumRepository

    // Channel to handle magnet responses
    private val magnetResponseChannel = Channel<MagnetResponseMessage>(UNLIMITED)

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<MusicCommunity>(MusicCommunity::class.java) {
        override fun create(): MusicCommunity {
            return MusicCommunity(settings, database, crawler)
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

    private fun onMagnetRequest(packet: Packet) {
        val (peer, request) = packet.getAuthPayload(MagnetRequestMessage)

        // Get the release from our local database
        val release = runBlocking { albumRepository.getReleaseById(request.releaseId) }

        if (release != null && release.magnet.isNotEmpty() && release.magnet != "access_restricted") {
            // If we have the release and its magnet link, send it back to the requesting peer
            val response =
                MagnetResponseMessage(
                    releaseId = request.releaseId,
                    magnetLink = release.magnet
                )

            val responsePacket =
                serializePacket(
                    MessageId.MAGNET_RESPONSE_MESSAGE,
                    response
                )

            send(peer, responsePacket)
        } else {
            // If we don't have the release or its magnet link is restricted, forward the request to our peers
            if (!request.checkTTL()) return

            val peers = getPeers().filter { it != peer } // Don't send back to the requesting peer
            for (otherPeer in peers) {
                try {
                    val forwardPacket =
                        serializePacket(
                            MessageId.MAGNET_REQUEST_MESSAGE,
                            request
                        )
                    send(otherPeer, forwardPacket)
                } catch (e: Exception) {
                    // Log error and continue with other peers
                }
            }
        }
    }

    private fun onMagnetResponse(packet: Packet) {
        val (_, response) = packet.getAuthPayload(MagnetResponseMessage)
        // Send the response to the channel
        magnetResponseChannel.trySend(response)
    }

    // Function to get a magnet response from the channel with timeout
    suspend fun getMagnetResponse(timeoutMillis: Long = 5000): MagnetResponseMessage? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMillis) {
                magnetResponseChannel.receive()
            }
        } catch (e: Exception) {
            null
        }
    }

    // Function to request a magnet link from peers
    fun requestMagnetLink(
        releaseId: String,
        ttl: UInt = 3u
    ): Int {
        val maxPeersToAsk = 20 // This is a magic number, tweak during/after experiments
        var count = 0
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
        return count
    }

    object MessageId {
        const val KEYWORD_SEARCH_MESSAGE = 10
        const val SWARM_HEALTH_MESSAGE = 11
        const val MAGNET_REQUEST_MESSAGE = 14
        const val MAGNET_RESPONSE_MESSAGE = 15
    }
}
