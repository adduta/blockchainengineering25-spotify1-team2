package nl.tudelft.trustchain.musicdao.ui.screens.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.ReleaseRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchScreenViewModel
    @Inject
    constructor(
        private val albumRepository: AlbumRepository,
        private val releaseRepository: ReleaseRepository,
        private val musicCommunity: MusicCommunity
    ) : ViewModel() {
        private val _isRefreshing: MutableLiveData<Boolean> = MutableLiveData()
        val isRefreshing: LiveData<Boolean> = _isRefreshing

        private val _searchQuery: MutableStateFlow<String> = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        private val _searchResult: MutableStateFlow<List<Album>> = MutableStateFlow(listOf())
        val searchResult: StateFlow<List<Album>> = _searchResult

        private val _peerAmount: MutableLiveData<Int> = MutableLiveData()
        var peerAmount: LiveData<Int> = _peerAmount

        private val _totalReleaseAmount: MutableLiveData<Int> = MutableLiveData()
        var totalReleaseAmount: LiveData<Int> = _totalReleaseAmount

        private var searchJob: Job? = null

        init {
            viewModelScope.launch {
                val userPublicKey = musicCommunity.publicKeyHex()
                // 1. Fetch albums only from cache for instant UI update
                var albums = albumRepository.getAlbumsFromCache(userPublicKey)
                _searchResult.value = downloadedFirstInListOfAlbums(albums)
                _peerAmount.value = musicCommunity.getPeers().size
                _totalReleaseAmount.value = albums.size
                // 2. In the background, refresh magnet links (do not block UI)
                refreshMagnetLinks(albums)

                // 3. Immediately refresh the cache (fetch from network)
                albumRepository.refreshCache()
                // 4. Fetch albums again after cache refresh
                albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                _searchResult.value = downloadedFirstInListOfAlbums(albums)
                _totalReleaseAmount.value = albums.size
                // 5. In the background, refresh magnet links for new albums
                refreshMagnetLinks(albums)
            }
        }

        fun searchDebounced(searchText: String) {
            _searchQuery.value = searchText

            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    delay(DEBOUNCE_DELAY)
                    search(searchText)
                }
        }

        fun downloadedFirstInListOfAlbums(list: List<Album>): List<Album> {
            // put downloaded albums first
            val downloadedAlbums =
                list.sortedBy { album ->
                    album.songs != null && album.songs.isNotEmpty()
                }.reversed()
            return downloadedAlbums
        }

        private suspend fun search(searchText: String) {
            val userPublicKey = musicCommunity.publicKeyHex()
            val albums = albumRepository.getAlbums(userPublicKey, releaseRepository, searchText)
            _searchResult.value = downloadedFirstInListOfAlbums(albums)
            refreshMagnetLinks(albums)
        }

        fun refresh() {
            viewModelScope.launch {
                _isRefreshing.value = true
                delay(500)
                if (_searchQuery.value.isEmpty()) {
                    val userPublicKey = musicCommunity.publicKeyHex()
                    val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                    _searchResult.value = downloadedFirstInListOfAlbums(albums)
                    _totalReleaseAmount.value = albums.size
                    refreshMagnetLinks(albums)
                }
                _peerAmount.value = musicCommunity.getPeers().size
                _isRefreshing.value = false
            }
        }

        /**
         * Refresh magnet links for all albums
         */
        private fun refreshMagnetLinks(albums: List<Album>) {
            viewModelScope.launch {
                albums.forEach { album ->
                    if (album.magnet == null ||
                        album.magnet.isEmpty() ||
                        album.magnet.isBlank() ||
                        album.magnet == "access_restricted" ||
                        album.magnet == "null" ||
                        album.magnet == "undefined"
                    ) {
                        try {
                            albumRepository.requestMagnetLink(album.id)
                        } catch (e: Exception) {
                            // Log error if needed
                        }
                    }
                }
            }
        }

        companion object {
            private const val DEBOUNCE_DELAY = 200L
        }
    }
