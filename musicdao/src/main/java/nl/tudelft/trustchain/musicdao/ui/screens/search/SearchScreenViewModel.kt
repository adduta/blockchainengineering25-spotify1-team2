package nl.tudelft.trustchain.musicdao.ui.screens.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
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
                val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                android.util.Log.d("SearchScreenViewModel", "Initial search: ${albums.size} albums")
                albums.forEach { album ->
                    android.util.Log.d("SearchScreenViewModel", "Initial album: id=${album.id}, magnet=${album.magnet}")
                }
                _searchResult.value = downloadedFirstInListOfAlbums(albums)
                _peerAmount.value = musicCommunity.getPeers().size
                _totalReleaseAmount.value = albums.size
                android.util.Log.d("SearchScreenViewModel", "Initial peerAmount: ${_peerAmount.value}, totalReleaseAmount: ${_totalReleaseAmount.value}")

                // Observe the albums flow for updates
                albumRepository.getAlbumsFlow(userPublicKey).map { x ->
                    android.util.Log.d("SearchScreenViewModel", "AlbumsFlow update: ${x.size} albums")
                    x.forEach { album ->
                        android.util.Log.d("SearchScreenViewModel", "Flow album: id=${album.id}, magnet=${album.magnet}")
                    }
                    if (_searchQuery.value.isEmpty()) {
                        _searchResult.value = downloadedFirstInListOfAlbums(albums)
                        _totalReleaseAmount.value = albums.size
                        _peerAmount.value = musicCommunity.getPeers().size
                        android.util.Log.d("SearchScreenViewModel", "Updated peerAmount: ${_peerAmount.value}, totalReleaseAmount: ${_totalReleaseAmount.value}")
                    }
                }
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
            if (searchText.isEmpty()) {
                val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                android.util.Log.d("SearchScreenViewModel", "Search (empty): ${albums.size} albums")
                albums.forEach { album ->
                    android.util.Log.d("SearchScreenViewModel", "Search album: id=${album.id}, magnet=${album.magnet}")
                }
                _searchResult.value = downloadedFirstInListOfAlbums(albums)
            } else {
                val result = albumRepository.searchAlbums(searchText)
                android.util.Log.d("SearchScreenViewModel", "Search (query): ${result.size} albums for query '$searchText'")
                result.forEach { album ->
                    android.util.Log.d("SearchScreenViewModel", "Search album: id=${album.id}, magnet=${album.magnet}")
                }
                _searchResult.value = downloadedFirstInListOfAlbums(result)
            }
        }

        fun refresh() {
            viewModelScope.launch {
                _isRefreshing.value = true
                delay(500)
                val userPublicKey = musicCommunity.publicKeyHex()
                val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                android.util.Log.d("SearchScreenViewModel", "Refresh: ${albums.size} albums")
                albums.forEach { album ->
                    android.util.Log.d("SearchScreenViewModel", "Refreshed album: id=${album.id}, magnet=${album.magnet}")
                }
                _searchResult.value = downloadedFirstInListOfAlbums(albums)
                _peerAmount.value = musicCommunity.getPeers().size
                _totalReleaseAmount.value = albums.size
                _isRefreshing.value = false
                android.util.Log.d("SearchScreenViewModel", "Refreshed peerAmount: ${_peerAmount.value}, totalReleaseAmount: ${_totalReleaseAmount.value}")
            }
        }

        companion object {
            private const val DEBOUNCE_DELAY = 200L
        }
    }
