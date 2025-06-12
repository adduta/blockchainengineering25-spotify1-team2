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
            val albumEntities: List<AlbumEntity> =
                if (searchText.isEmpty()) {
                    database.dao.getAll()
                } else {
                    database.dao.localSearch(searchText)
                }

            // For each album, check if we need to request the magnet link
            for (album in albumEntities) {
                if (album.magnet == "access_restricted" || album.magnet.isEmpty()) {
                    val userPublicKeyBytes = userPublicKey.hexToBytes()
                    val isUltimate = userTierVerifier.isUltimateUser(userPublicKeyBytes)
                    val isPro = userTierVerifier.isProUser(userPublicKeyBytes)
                    val isPastDelay = isReleasePastDelayPeriod(album.releaseDate)

                    if (album.isExclusive) {
                        if (isUltimate) {
                            Log.d("AlbumRepository", "Found exclusive album ${album.id}, requesting magnet link for Ultimate user")
                            val magnetLink = requestMagnetLink(album.id)
                            if (magnetLink.isNullOrEmpty()) {
                                Log.w("AlbumRepository", "Failed to get magnet link for exclusive album ${album.id}")
                                withContext(Dispatchers.IO) {
                                    database.dao.updateReleaseMagnet(album.id, "", "")
                                }
                            }
                        } else {
                            Log.d("AlbumRepository", "Found exclusive album ${album.id}, but user (${userPublicKey.take(8)}...) is not Ultimate tier")
                        }
                    } else if (isPro || isPastDelay) {
                        Log.d("AlbumRepository", "Found album ${album.id}, requesting magnet link for Pro user or past delay period")
                        val magnetLink = requestMagnetLink(album.id)
                        if (magnetLink.isNullOrEmpty()) {
                            Log.w("AlbumRepository", "Failed to get magnet link for album ${album.id}")
                            withContext(Dispatchers.IO) {
                                database.dao.updateReleaseMagnet(album.id, "", "")
                            }
                        }
                    } else {
                        Log.d("AlbumRepository", "Found album ${album.id}, but user is not Pro and release is not past delay period")
                    }
                } else {
                    Log.d("AlbumRepository", "Found existing magnet link for album ${album.id}: ${album.magnet}")
                    if (album.infoHash.isNullOrEmpty() && album.magnet.isNotEmpty()) {
                        Log.d("AlbumRepository", "For album ${album.id}, the magnet link was set but infoHash was not. Persisting infoHash.")
                        val infoHash: String = TorrentEngine.magnetToInfoHash(album.magnet) ?: ""
                        withContext(Dispatchers.IO) {
                            database.dao.updateReleaseMagnet(album.id, album.magnet, infoHash)
                        }
                    }
                }
            }

            // Filter albums based on user tier and release date
            val albums = albumEntities.map { it.toAlbum() }
            return albums
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
                        Log.d("AlbumRepository", "No magnet link found for release ${entity.id}, checking access requirements")
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val userPublicKeyBytes = userPublicKey.hexToBytes()
                                val isUltimate = userTierVerifier.isUltimateUser(userPublicKeyBytes)
                                val isPro = userTierVerifier.isProUser(userPublicKeyBytes)
                                val isPastDelay = isReleasePastDelayPeriod(entity.releaseDate)

                                if (entity.isExclusive) {
                                    if (isUltimate) {
                                        Log.d("AlbumRepository", "Requesting magnet link for exclusive album ${entity.id} for Ultimate user")
                                        requestMagnetLink(entity.id)
                                    } else {
                                        Log.d("AlbumRepository", "Skipping magnet link request for exclusive album ${entity.id} - user is not Ultimate tier")
                                    }
                                } else if (isPro || isPastDelay) {
                                    Log.d("AlbumRepository", "Requesting magnet link for album ${entity.id} for Pro user or past delay period")
                                    requestMagnetLink(entity.id)
                                } else {
                                    Log.d("AlbumRepository", "Skipping magnet link request for album ${entity.id} - user is not Pro and release is not past delay period")
                                }
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
            releaseDate: String,
            isExclusive: Boolean = false
        ): Boolean {
            try {
                val block =
                    releasePublishBlockRepository.create(
                        releaseId = releaseId,
                        magnet = "",
                        title = title,
                        artist = artist,
                        releaseDate = releaseDate,
                        isExclusive = isExclusive
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
                                torrentPath = null,
                                isExclusive = isExclusive
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

        fun requestMagnetLink(releaseId: String): String? {
            try {
                // Request magnet link from peers
                val peersCount = musicCommunity.requestMagnetLink(releaseId)
                Log.d("AlbumRepository", "Sent magnet link request to $peersCount peers for release $releaseId")
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
                    val isPastDelay = isReleasePastDelayPeriod(album.releaseDate.toString())

                    // For non-exclusive releases, Pro users or past delay period
                    Log.d("AlbumRepository", "[getAlbumsFromCache] Album ${album.id} is access_restricted, user is PRO: $isPro")
                    isPro || isPastDelay
                } else {
                    true
                }
            }
        }
    }
