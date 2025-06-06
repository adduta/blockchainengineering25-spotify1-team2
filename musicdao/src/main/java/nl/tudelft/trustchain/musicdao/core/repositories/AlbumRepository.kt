package nl.tudelft.trustchain.musicdao.core.repositories

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import javax.inject.Inject

/**
 * This class will be the class the application interacts with and will return
 * the data that the UI/CMD interface can work with.
 */
class AlbumRepository
    @Inject
    constructor(
        private val database: CacheDatabase,
        private val releasePublishBlockRepository: ReleasePublishBlockRepository,
        private val musicCommunity: MusicCommunity
    ) {
        suspend fun getReleaseById(releaseId: String): Album? {
            val album = database.dao.get(releaseId)?.toAlbum()
            if (album != null && album.magnet.isEmpty()) {
                Log.d("AlbumRepository", "No magnet link found for release $releaseId, requesting from peers")
                // If we don't have the magnet link, request it from peers
                requestMagnetLink(releaseId)
            } else if (album != null) {
                Log.d("AlbumRepository", "Found existing magnet link for release $releaseId")
            }
            return album
        }

        suspend fun getAlbums(
            userPublicKey: String,
            releaseRepository: ReleaseRepository
        ): List<Album> {
            return withContext(Dispatchers.IO) {
                database.dao.getAll().map { entity ->
                    val magnetLink =
                        if (entity.magnet.isEmpty()) {
                            Log.d("AlbumRepository", "No magnet link found for release ${entity.id}, requesting from peers")
                            // If we don't have the magnet link, request it from peers
                            requestMagnetLink(entity.id)
                            null // Return null for now, it will be updated when we get the response
                        } else {
                            Log.d("AlbumRepository", "Found existing magnet link for release ${entity.id}")
                            releaseRepository.getFullRelease(entity.id, userPublicKey)
                        }
                    entity.toAlbum().copy(
                        magnet = magnetLink ?: "access_restricted"
                    )
                }
            }
        }

        fun getAlbumsFlow(userPublicKey: String): LiveData<List<Album>> {
            return database.dao.getAllLiveData().map { entities ->
                entities.map { entity ->
                    // Note: This is called from a non-suspend context, so we can't use getFullRelease here
                    if (entity.magnet.isEmpty()) {
                        Log.d("AlbumRepository", "No magnet link found for release ${entity.id}, requesting from peers in background")
                        // Request magnet link in background
                        GlobalScope.launch {
                            requestMagnetLink(entity.id)
                        }
                    } else {
                        Log.d("AlbumRepository", "Found existing magnet link for release ${entity.id}")
                    }
                    entity.toAlbum().copy(
                        magnet = "access_restricted"
                    )
                }
            }
        }

        suspend fun getAlbumsFromArtist(publicKey: String): List<Album> {
            return database.dao.getFromArtist(publicKey = publicKey).map { it.toAlbum() }
        }

        suspend fun searchAlbums(keyword: String): List<Album> {
            return database.dao.localSearch(keyword).map { it.toAlbum() }
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

        private suspend fun requestMagnetLink(releaseId: String) {
            try {
                Log.d("AlbumRepository", "Requesting magnet link for release $releaseId from peers")
                // Request magnet link from peers
                val peersCount = musicCommunity.requestMagnetLink(releaseId)
                Log.d("AlbumRepository", "Sent magnet link request to $peersCount peers for release $releaseId")
                
                // Wait for response with timeout
                val response = musicCommunity.getMagnetResponse()
                
                // If we got a valid response, update the local database
                if (response != null && response.magnetLink.isNotEmpty()) {
                    Log.d("AlbumRepository", "Received magnet link for release $releaseId, updating local database")
                    database.dao.updateReleaseMagnet(releaseId, response.magnetLink)
                } else {
                    Log.d("AlbumRepository", "No magnet link response received for release $releaseId")
                }
            } catch (e: Exception) {
                // Log error and continue
                Log.e("AlbumRepository", "Error requesting magnet link for release $releaseId: ${e.message}")
            }
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

        suspend fun updateReleaseMagnet(
            releaseId: String,
            magnetLink: String
        ) {
            Log.d("AlbumRepository", "Updating magnet link for release $releaseId")
            database.dao.updateReleaseMagnet(releaseId, magnetLink)
        }
    }
