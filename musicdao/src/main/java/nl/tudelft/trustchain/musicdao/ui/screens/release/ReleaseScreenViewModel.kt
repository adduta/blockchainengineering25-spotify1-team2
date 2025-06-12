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

        private val _release: MutableStateFlow<Album?> = MutableStateFlow(null)
        val release: StateFlow<Album?> = _release

        private val _torrentState: MutableStateFlow<TorrentStatus?> = MutableStateFlow(null)
        val torrentState: StateFlow<TorrentStatus?> = _torrentState

        private val _accessReason: MutableStateFlow<AccessReason?> = MutableStateFlow(null)
        val accessReason: StateFlow<AccessReason?> = _accessReason

        private val releaseLiveData: LiveData<AlbumEntity> = database.dao.getLiveData(releaseId)
        val saturatedReleaseState: LiveData<Album> = releaseLiveData.map { it.toAlbum() }

        init {
            viewModelScope.launch {
                val albumEntity = database.dao.get(releaseId)
                _release.value = albumEntity?.toAlbum()

                albumEntity?.let { _albumEntity ->
                    // Determine access reason
                    _accessReason.value =
                        when {
                            _albumEntity.magnet == "access_restricted" || _albumEntity.magnet.isEmpty() -> {
                                val isUltimate = userTierVerifier.isUltimateUser(musicCommunity.publicKeyHex().hexToBytes())
                                val isPro = userTierVerifier.isProUser(musicCommunity.publicKeyHex().hexToBytes())
                                val releaseDate = Instant.parse(_albumEntity.releaseDate)
                                val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)

                                if (_albumEntity.isExclusive) {
                                    if (isUltimate) {
                                        // For Ultimate users, request magnet link for exclusive content
                                        try {
                                            val magnetLink = albumRepository.requestMagnetLink(_albumEntity.id)
                                            if (magnetLink != null) {
                                                val infoHash = TorrentEngine.magnetToInfoHash(magnetLink)
                                                if (infoHash != null) {
                                                    database.dao.updateReleaseMagnet(_albumEntity.id, magnetLink, infoHash)
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
                                        AccessReason.EXCLUSIVE
                                    }
                                } else if (isPro || releaseDate.isBefore(sevenDaysAgo)) {
                                    // For Pro users or after delay period, request magnet link
                                    try {
                                        val magnetLink = albumRepository.requestMagnetLink(_albumEntity.id)
                                        if (magnetLink != null) {
                                            val infoHash = TorrentEngine.magnetToInfoHash(magnetLink)
                                            if (infoHash != null) {
                                                database.dao.updateReleaseMagnet(_albumEntity.id, magnetLink, infoHash)
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
                            _albumEntity.magnet.isEmpty() -> AccessReason.NO_MAGNET
                            !_albumEntity.isDownloaded && _albumEntity.magnet.isNotEmpty() -> {
                                try {
                                    // Convert magnet to infoHash if not already set
                                    if (_albumEntity.infoHash == null && _albumEntity.magnet.isNotEmpty()) {
                                        val infoHash = TorrentEngine.magnetToInfoHash(_albumEntity.magnet)
                                        if (infoHash != null) {
                                            database.dao.updateReleaseMagnet(_albumEntity.id, _albumEntity.magnet, infoHash)
                                        }
                                    }
                                    torrentEngine.download(_albumEntity.magnet)
                                    AccessReason.DOWNLOADING
                                } catch (e: Exception) {
                                    Log.e("ReleaseScreenViewModel", "Error downloading torrent: ${e.message}")
                                    AccessReason.DOWNLOAD_ERROR
                                }
                            }
                            else -> null
                        }

                    // Skip download for access-restricted releases
                    if (_albumEntity.magnet != "access_restricted") {
                        if (!_albumEntity.isDownloaded && _albumEntity.magnet.isNotEmpty()) {
                            try {
                                // Ensure infoHash is set before downloading
                                if (_albumEntity.infoHash == null) {
                                    val infoHash = TorrentEngine.magnetToInfoHash(_albumEntity.magnet)
                                    if (infoHash != null) {
                                        database.dao.updateReleaseMagnet(_albumEntity.id, _albumEntity.magnet, infoHash)
                                    }
                                }
                                torrentEngine.download(_albumEntity.magnet)
                            } catch (e: Exception) {
                                Log.e("ReleaseScreenViewModel", "Error downloading torrent: ${e.message}")
                            }
                        }

                        // Start monitoring torrent status
                        while (isActive) {
                            try {
                                // Get latest release data to ensure we have the most recent infoHash
                                val currentRelease = database.dao.get(releaseId)
                                Log.d("ReleaseScreenViewModel", "Current release: $currentRelease, infoHash: ${currentRelease.infoHash}")

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
            EXCLUSIVE, // Release is exclusive (needs ultimate)
            NO_MAGNET, // No magnet link available
            DOWNLOADING, // Currently downloading
            DOWNLOAD_ERROR, // Error occurred during download
            WAITING_PERIOD // Release is in waiting period for basic users
        }
    }
