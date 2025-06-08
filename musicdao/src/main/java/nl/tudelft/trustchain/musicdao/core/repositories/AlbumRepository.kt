package nl.tudelft.trustchain.musicdao.core.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.UserTierVerifier
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockRepository
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetResponseMessage
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import javax.inject.Inject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * This class will be the class the application interacts with and will return
 * the data that the UI/CMD interface can work with.
 */
class AlbumRepository
    @Inject
    constructor(
        private val database: CacheDatabase,
        private val releasePublishBlockRepository: ReleasePublishBlockRepository,
        private val musicCommunity: MusicCommunity,
        private val userTierVerifier: UserTierVerifier
    ) {
        suspend fun getReleaseById(id: String): Album? {
            val album = database.dao.get(id)?.toAlbum()
            if (album != null) {
                if (album.magnet == "access_restricted") {
                    Log.d("AlbumRepository", "Found album with access_restricted magnet, requesting magnet link")
                    requestMagnetLink(id)
                } else if (album.magnet.isEmpty()) {
                    Log.d("AlbumRepository", "Found album without magnet link, requesting magnet link")
                    requestMagnetLink(id)
                } else {
                    Log.d("AlbumRepository", "Found existing magnet link for release $id")
                }
            }
            return album
        }

        private fun isReleasePastDelayPeriod(releaseDate: String): Boolean {
            try {
                val releaseInstant = Instant.parse(releaseDate)
                val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
                return releaseInstant.isBefore(sevenDaysAgo)
            } catch (e: Exception) {
                Log.e("AlbumRepository", "Error parsing release date: ${e.message}")
                return false
            }
        }

        suspend fun getAlbums(
            userPublicKey: String,
            releaseRepository: ReleaseRepository,
            searchText: String = ""
        ): List<Album> {
            val albumEntities: List<AlbumEntity> = if (searchText.isEmpty()) {
                database.dao.getAll()
            } else {
                database.dao.localSearch(searchText)
            }

            // For each album, check if we need to request the magnet link
            for (album in albumEntities) {
                if (album.magnet == "access_restricted") {
                    val isPro = userTierVerifier.isProUser(userPublicKey.hexToBytes())
                    val isPastDelay = isReleasePastDelayPeriod(album.releaseDate)

                    if (isPro || isPastDelay) {
                        Log.d("AlbumRepository", "Found album ${album.id} with access_restricted magnet, requesting magnet link")
                        val magnetLink = requestMagnetLink(album.id)
                        if (magnetLink.isNullOrEmpty()) {
                            withContext(Dispatchers.IO) {
                                database.dao.updateReleaseMagnet(album.id, "", "")
                            }
                        }
                    }
                } else if (album.magnet.isEmpty()) {
                    Log.d("AlbumRepository", "Found album ${album.id} without magnet link, requesting magnet link")
                    val magnetLink = requestMagnetLink(album.id)
                    if (magnetLink.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            database.dao.updateReleaseMagnet(album.id, "", "")
                        }
                    }
                } else {
                    Log.d(
                        "AlbumRepository",
                        "Found existing magnet link for album ${album.id}: ${album.magnet}"
                    )
                    if (album.infoHash.isNullOrEmpty() && album.magnet.isNotEmpty()) {
                        Log.d(
                            "AlbumRepository",
                            "For album ${album.id}, the magnet link was set," +
                                " but the info has was not set. So, the infoHash will now be persisted."
                        )
                        val infoHash: String = TorrentEngine.magnetToInfoHash(album.magnet) ?: ""
                        withContext(Dispatchers.IO) {
                            database.dao.updateReleaseMagnet(album.id, album.magnet, infoHash)
                        }
                    }
                }
            }

            // Filter albums based on user tier and release date
            val albums = albumEntities.map { it.toAlbum() }
            return albums.filter { album ->
                if (album.magnet == "access_restricted") {
                    val isPro = userTierVerifier.isProUser(userPublicKey.hexToBytes())
                    val isPastDelay = isReleasePastDelayPeriod(album.releaseDate.toString())
                    Log.d("AlbumRepository", "Album ${album.id} is access_restricted, user is PRO: $isPro, past delay: $isPastDelay")
                    isPro || isPastDelay
                } else {
                    true
                }
            }
        }

        fun getAlbumsFlow(userPublicKey: String): LiveData<List<Album>> {
            Log.d("AlbumRepository", "Getting albums flow for user $userPublicKey")
            return database.dao.getAllLiveData().map { entities ->
                Log.d("AlbumRepository", "Processing ${entities.size} albums in flow")
                if (entities.isEmpty()) {
                    Log.w("AlbumRepository", "WARNING: No albums found in database!")
                }

                entities.map { entity ->
                    Log.d("AlbumRepository", "Processing album ${entity.id} in flow with magnet: ${entity.magnet}")
                    // Always return the album immediately, even without magnet link
                    val album =
                        entity.toAlbum().copy(
                            magnet = if (entity.magnet.isEmpty()) "access_restricted" else entity.magnet
                        )

                    // If no magnet link, request it in background
                    if (entity.magnet.isEmpty()) {
                        Log.d("AlbumRepository", "No magnet link found for release ${entity.id}, requesting from peers in background")
                        // Use a more controlled coroutine scope
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                requestMagnetLink(entity.id)
                            } catch (e: Exception) {
                                Log.e("AlbumRepository", "Error requesting magnet link in background: ${e.message}")
                            }
                        }
                    } else {
                        Log.d("AlbumRepository", "Found existing magnet link for release ${entity.id}")
                    }

                    album
                }
            }
        }

        suspend fun getAlbumsFromArtist(publicKey: String): List<Album> {
            return database.dao.getFromArtist(publicKey = publicKey).map { it.toAlbum() }
        }

        @OptIn(DelicateCoroutinesApi::class)
        suspend fun createAlbum(
            releaseId: String,
            magnet: String,
            title: String,
            artist: String,
            releaseDate: String
        ): Boolean {
            try {
                // Create and publish the Trustchain block without the magnet link
                val block =
                    releasePublishBlockRepository.create(
                        releaseId = releaseId,
                        magnet = "",
                        title = title,
                        artist = artist,
                        releaseDate = releaseDate
                    )

                if (block != null) {
                    try {
                        val transaction: ReleasePublishBlock =
                            releasePublishBlockRepository.toBlock(block)

                        // Store the magnet link locally for the artist
                        val infoHash =
                            if (magnet.isNotEmpty()) {
                                TorrentEngine.magnetToInfoHash(magnet)
                            } else {
                                null
                            }

                        database.dao.insert(
                            AlbumEntity(
                                id = transaction.releaseId,
                                magnet = magnet,
                                title = transaction.title,
                                artist = transaction.artist,
                                publisher = transaction.publisher,
                                releaseDate = transaction.releaseDate,
                                songs = listOf(),
                                cover = null,
                                root = null,
                                isDownloaded = false,
                                infoHash = infoHash,
                                torrentPath = null
                            )
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e("AlbumRepository", "Error inserting album: ${e.message}")
                        return false
                    }
                }
                return false
            } catch (e: Exception) {
                Log.e("AlbumRepository", "Error creating album block: ${e.message}")
                return false
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        suspend fun requestMagnetLink(releaseId: String): String? {
            try {
                Log.d("AlbumRepository", "Requesting magnet link for release $releaseId from peers")

                // Try up to 3 times with increasing timeouts
                val timeouts = listOf(5000L, 10000L, 15000L)

                var response: MagnetResponseMessage? = null

                for ((attempt, timeout) in timeouts.withIndex()) {
                    Log.d("AlbumRepository", "Attempt ${attempt + 1} to get magnet link for release $releaseId with timeout ${timeout}ms")

                    // Request magnet link from peers
                    val peersCount = musicCommunity.requestMagnetLink(releaseId)
                    Log.d("AlbumRepository", "Sent magnet link request to $peersCount peers for release $releaseId")

                    // Wait for response with timeout
                    response = musicCommunity.getMagnetResponse(timeout)

                    if (response != null && response.magnetLink.isNotEmpty()) {
                        Log.d("AlbumRepository", "Received magnet link for release $releaseId on attempt ${attempt + 1}")
                        break
                    } else {
                        Log.d("AlbumRepository", "No magnet link response received for release $releaseId on attempt ${attempt + 1}")
                    }
                }

                // If we got a valid response, update the local database
                if (response != null && response.magnetLink.isNotEmpty()) {
                    Log.d("AlbumRepository", "Updating local database with magnet link for release $releaseId")
                    val infoHash: String = TorrentEngine.magnetToInfoHash(response.magnetLink) ?: ""
                    withContext(Dispatchers.IO) {
                        database.dao.updateReleaseMagnet(releaseId, response.magnetLink, infoHash)
                    }
                    return response.magnetLink
                } else {
                    Log.d("AlbumRepository", "Failed to get magnet link for release $releaseId after all attempts")
                }
            } catch (e: Exception) {
                // Log error and continue
                Log.e("AlbumRepository", "Error requesting magnet link for release $releaseId: ${e.message}")
            }
            return null
        }

        @OptIn(DelicateCoroutinesApi::class)
        suspend fun refreshCache() {
            val releaseBlocks = releasePublishBlockRepository.getValidBlocks()

            releaseBlocks.forEach {
                // For old format blocks that have a magnet link, we'll store it locally
                val magnet =
                    if (it.magnet != null && it.magnet.isNotEmpty()) {
                        it.magnet
                    } else {
                        "access_restricted"
                    }

                // Safely get infoHash only if we have a valid magnet link
                val infoHash =
                    if (magnet != "access_restricted") {
                        try {
                            TorrentEngine.magnetToInfoHash(magnet)
                        } catch (e: Exception) {
                            Log.e("AlbumRepository", "Error parsing magnet link: ${e.message}")
                            null
                        }
                    } else {
                        null
                    }

                // Update or insert the album entity
                database.dao.insert(
                    AlbumEntity(
                        id = it.releaseId,
                        magnet = magnet,
                        title = it.title,
                        artist = it.artist,
                        publisher = it.publisher,
                        releaseDate = it.releaseDate,
                        songs = listOf(),
                        cover = null,
                        root = null,
                        isDownloaded = false,
                        infoHash = infoHash,
                        torrentPath = null
                    )
                )
            }

            // Force a database update to ensure changes are persisted
            database.dao.getAll()
            // Log the refresh for debugging
            Log.d("AlbumRepository", "Cache refreshed with ${releaseBlocks.size} releases")
        }

        /**
         * Fetch albums only from the local database/cache, without any network or magnet link requests.
         * This is fast and always returns immediately.
         */
        suspend fun getAlbumsFromCache(userPublicKey: String): List<Album> {
            val albumEntities = database.dao.getAll()
            Log.d("AlbumRepository", "[getAlbumsFromCache] Found ${albumEntities.size} albums in database")
            val albums = albumEntities.map { it.toAlbum() }
            return albums.filter { album ->
                if (album.magnet == "access_restricted") {
                    val isPro = userTierVerifier.isProUser(userPublicKey.hexToBytes())
                    Log.d("AlbumRepository", "[getAlbumsFromCache] Album ${album.id} is access_restricted, user is PRO: $isPro")
                    isPro
                } else {
                    true
                }
            }
        }
    }
