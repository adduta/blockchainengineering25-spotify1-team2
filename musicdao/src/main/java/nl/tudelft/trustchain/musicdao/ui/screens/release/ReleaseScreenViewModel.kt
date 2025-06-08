package nl.tudelft.trustchain.musicdao.ui.screens.release

import android.util.Log
import androidx.lifecycle.*
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import nl.tudelft.trustchain.musicdao.core.torrent.status.TorrentStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.UserTierVerifier
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(DelicateCoroutinesApi::class)
class ReleaseScreenViewModel
    @AssistedInject
    constructor(
        @Assisted private val releaseId: String,
        private val database: CacheDatabase,
        private val torrentEngine: TorrentEngine,
        private val userTierVerifier: UserTierVerifier,
        private val musicCommunity: MusicCommunity,
        private val albumRepository: AlbumRepository
    ) : ViewModel() {
        @AssistedFactory
        interface ReleaseScreenViewModelFactory {
            fun create(releaseId: String): ReleaseScreenViewModel
        }

        companion object {
            fun provideFactory(
                assistedFactory: ReleaseScreenViewModelFactory,
                releaseId: String
            ): ViewModelProvider.Factory =
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return assistedFactory.create(releaseId) as T
                    }
                }
        }

        private var releaseLiveData: LiveData<AlbumEntity> = MutableLiveData(null)
        var saturatedReleaseState: LiveData<Album?> = MutableLiveData()

        private val _torrentState: MutableStateFlow<TorrentStatus?> = MutableStateFlow(null)
        val torrentState: StateFlow<TorrentStatus?> = _torrentState

        private val _accessReason: MutableStateFlow<AccessReason?> = MutableStateFlow(null)
        val accessReason: StateFlow<AccessReason?> = _accessReason

        init {
            viewModelScope.launch {
                releaseLiveData = database.dao.getLiveData(releaseId)
                saturatedReleaseState = releaseLiveData.map { it.toAlbum() }

                val release = database.dao.get(releaseId)

                release?.let { _release ->
                    // Determine access reason
                    _accessReason.value =
                        when {
                            _release.magnet == "access_restricted" -> {
                                val isPro = userTierVerifier.isProUser(musicCommunity.publicKeyHex().hexToBytes())
                                val releaseDate = Instant.parse(_release.releaseDate)
                                val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)

                                if (isPro) {
                                    // Pro users can access immediately
                                    try {
                                        val magnetLink = albumRepository.requestMagnetLink(_release.id)
                                        if (magnetLink != null) {
                                            val infoHash = TorrentEngine.magnetToInfoHash(magnetLink)

                                            if (infoHash != null) {
                                                // Update magnet and infoHash in the database
                                                database.dao.updateReleaseMagnet(
                                                    _release.id,
                                                    magnetLink,
                                                    infoHash
                                                )
                                                AccessReason.DOWNLOADING
                                            } else {
                                                AccessReason.NO_MAGNET
                                            }
                                        } else {
                                            AccessReason.NO_MAGNET
                                        }
                                    } catch (e: Exception) {
                                        AccessReason.DOWNLOAD_ERROR
                                    }
                                } else if (releaseDate.isBefore(sevenDaysAgo)) {
                                    // Basic users can access after 7 days
                                    try {
                                        val magnetLink = albumRepository.requestMagnetLink(_release.id)
                                        if (magnetLink != null) {
                                            val infoHash = TorrentEngine.magnetToInfoHash(magnetLink)

                                            if (infoHash != null) {
                                                // Update magnet and infoHash in the database
                                                database.dao.updateReleaseMagnet(
                                                    _release.id,
                                                    magnetLink,
                                                    infoHash
                                                )
                                                AccessReason.DOWNLOADING
                                            } else {
                                                AccessReason.NO_MAGNET
                                            }
                                        } else {
                                            AccessReason.NO_MAGNET
                                        }
                                    } catch (e: Exception) {
                                        AccessReason.DOWNLOAD_ERROR
                                    }
                                } else {
                                    AccessReason.WAITING_PERIOD
                                }
                            }
                            _release.magnet.isEmpty() -> AccessReason.NO_MAGNET
                            !_release.isDownloaded && _release.magnet.isNotEmpty() -> {
                                try {
                                    // Convert magnet to infoHash if not already set
                                    if (_release.infoHash == null && _release.magnet.isNotEmpty()) {
                                        val infoHash = TorrentEngine.magnetToInfoHash(_release.magnet)
                                        if (infoHash != null) {
                                            database.dao.updateReleaseMagnet(_release.id, _release.magnet, infoHash)
                                        }
                                    }
                                    torrentEngine.download(_release.magnet)
                                    AccessReason.DOWNLOADING
                                } catch (e: Exception) {
                                    Log.e("ReleaseScreenViewModel", "Error downloading torrent: ${e.message}")
                                    AccessReason.DOWNLOAD_ERROR
                                }
                            }
                            else -> null
                        }

                    // Skip download for access-restricted releases
                    if (_release.magnet != "access_restricted") {
                        if (!_release.isDownloaded && _release.magnet.isNotEmpty()) {
                            try {
                                // Ensure infoHash is set before downloading
                                if (_release.infoHash == null) {
                                    val infoHash = TorrentEngine.magnetToInfoHash(_release.magnet)
                                    if (infoHash != null) {
                                        database.dao.updateReleaseMagnet(_release.id, _release.magnet, infoHash)
                                    }
                                }
                                torrentEngine.download(_release.magnet)
                            } catch (e: Exception) {
                                Log.e("ReleaseScreenViewModel", "Error downloading torrent: ${e.message}")
                            }
                        }

                        // Start monitoring torrent status
                        while (isActive) {
                            try {
                                // Get latest release data to ensure we have the most recent infoHash
                                val currentRelease = database.dao.get(releaseId)
                                if (currentRelease?.infoHash != null) {
                                    val status = torrentEngine.getTorrentStatus(currentRelease.infoHash)
                                    if (status != null) {
                                        _torrentState.value = status
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ReleaseScreenViewModel", "Error getting torrent status: ${e.message}")
                            }
                            delay(1000L)
                        }
                    }
                }
            }
        }

        enum class AccessReason {
            RESTRICTED, // Release is restricted (needs pro or waiting period)
            NO_MAGNET, // No magnet link available
            DOWNLOADING, // Currently downloading
            DOWNLOAD_ERROR, // Error occurred during download
            WAITING_PERIOD // Release is in waiting period for basic users
        }
    }
