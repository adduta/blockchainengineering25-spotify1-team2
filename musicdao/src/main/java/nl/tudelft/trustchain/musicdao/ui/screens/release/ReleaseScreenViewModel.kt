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

@OptIn(DelicateCoroutinesApi::class)
class ReleaseScreenViewModel
    @AssistedInject
    constructor(
        @Assisted private val releaseId: String,
        private val database: CacheDatabase,
        private val torrentEngine: TorrentEngine,
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
                            _release.magnet == "access_restricted" -> AccessReason.RESTRICTED
                            _release.magnet.isEmpty() -> AccessReason.NO_MAGNET
                            !_release.isDownloaded && _release.magnet.isNotEmpty() -> {
                                try {
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
                                torrentEngine.download(_release.magnet)
                            } catch (e: Exception) {
                                Log.e("ReleaseScreenViewModel", "Error downloading torrent: ${e.message}")
                            }
                        }

                        while (isActive) {
                            if (_release.infoHash != null) {
                                try {
                                    _torrentState.value = torrentEngine.getTorrentStatus(_release.infoHash)
                                } catch (e: Exception) {
                                    Log.e("ReleaseScreenViewModel", "Error getting torrent status: ${e.message}")
                                }
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
            DOWNLOAD_ERROR // Error occurred during download
        }
    }
