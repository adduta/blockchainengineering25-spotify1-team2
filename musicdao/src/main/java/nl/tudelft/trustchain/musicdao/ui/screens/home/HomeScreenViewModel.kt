package nl.tudelft.trustchain.musicdao.ui.screens.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.ReleaseRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel
    @Inject
    constructor(
        private val albumRepository: AlbumRepository,
        private val releaseRepository: ReleaseRepository,
        private val musicCommunity: MusicCommunity
    ) : ViewModel() {
        private val _releases: MutableLiveData<List<Album>> = MutableLiveData()
        val releases: LiveData<List<Album>> = _releases

        private val _peerAmount: MutableLiveData<Int> = MutableLiveData()
        val peerAmount: LiveData<Int> = _peerAmount

        private val _totalReleaseAmount: MutableLiveData<Int> = MutableLiveData()
        val totalReleaseAmount: LiveData<Int> = _totalReleaseAmount

        // Add debug state
        private val _debugState: MutableLiveData<String> = MutableLiveData()
        val debugState: LiveData<String> = _debugState

        private var albumsFlowObserver: androidx.lifecycle.Observer<List<Album>>? = null

        private fun log(message: String) {
            val timestamp = java.time.LocalDateTime.now().toString()
            val newMessage = "[$timestamp] $message\n"
            _debugState.postValue((_debugState.value ?: "") + newMessage)
            android.util.Log.d("HomeScreenViewModel", message)
        }

        init {
            viewModelScope.launch {
                try {
                    // Get the user's public key from MusicCommunity
                    val userPublicKey = musicCommunity.publicKeyHex()
                    log("Starting initialization with userPublicKey: $userPublicKey")

                    // Set initial peer count
                    _peerAmount.postValue(musicCommunity.getPeers().size)
                    log("Initial peer count: ${musicCommunity.getPeers().size}")

                    // Set up the albums flow observer first
                    log("Setting up albums flow observer...")
                    val albumsFlow = albumRepository.getAlbumsFlow(userPublicKey)
                    albumsFlowObserver = androidx.lifecycle.Observer { albums ->
                        log("AlbumsFlow update received: ${albums.size} albums")
                        if (albums.isEmpty()) {
                            log("WARNING: AlbumsFlow update is empty!")
                        } else {
                            albums.forEach { album ->
                                log("Flow album: id=${album.id}, title=${album.title}, magnet=${album.magnet}")
                            }
                        }

                        // For each album, fetch the magnet link in the background if needed
                        viewModelScope.launch {
                            refreshMagnetLinks(albums, userPublicKey)
                        }

                        _releases.postValue(albums)
                        _totalReleaseAmount.postValue(albums.size)
                        _peerAmount.postValue(musicCommunity.getPeers().size)
                        log("Flow update complete - releases: ${_releases.value?.size}, total: ${_totalReleaseAmount.value}, peers: ${_peerAmount.value}")
                    }
                    albumsFlow.observeForever(albumsFlowObserver!!)

                    // Initial load of albums
                    log("Fetching initial albums...")
                    val initialAlbums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                    log("Initial load complete: ${initialAlbums.size} albums")

                    if (initialAlbums.isEmpty()) {
                        log("WARNING: Initial albums list is empty!")
                    } else {
                        initialAlbums.forEach { album ->
                            log("Initial album: id=${album.id}, title=${album.title}, magnet=${album.magnet}")
                        }
                    }

                    // Update LiveData values immediately
                    log("Updating LiveData values...")
                    _releases.postValue(initialAlbums)
                    _totalReleaseAmount.postValue(initialAlbums.size)
                    _peerAmount.postValue(musicCommunity.getPeers().size)
                    log("LiveData values updated - releases: ${_releases.value?.size}, total: ${_totalReleaseAmount.value}, peers: ${_peerAmount.value}")

                    // Set up periodic peer count updates
                    viewModelScope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(5000) // Update every 5 seconds
                            val currentPeers = musicCommunity.getPeers().size
                            if (currentPeers != _peerAmount.value) {
                                log("Peer count changed: ${_peerAmount.value} -> $currentPeers")
                                _peerAmount.postValue(currentPeers)
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("Error in initialization: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        /**
         * Force refresh magnet links for all albums
         * This should be called when the user's pro status changes
         */
        private fun refreshMagnetLinks(
            albums: List<Album>,
            userPublicKey: String
        ) {
            viewModelScope.launch {
                try {
                    log("Refreshing magnet links for ${albums.size} albums")
                    albums.forEach { album ->
                        if (album.magnet == "access_restricted") {
                            log("Requesting magnet link for album ${album.id}")
                            try {
                                albumRepository.requestMagnetLink(album.id)
                            } catch (e: Exception) {
                                log("Error requesting magnet link for album ${album.id}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("Error refreshing magnet links: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                try {
                    log("Manual refresh started...")
                    val userPublicKey = musicCommunity.publicKeyHex()
                    val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                    log("Manual refresh: ${albums.size} albums")
                    albums.forEach { album ->
                        log("Refreshed album: id=${album.id}, title=${album.title}, magnet=${album.magnet}")
                    }
                    _releases.postValue(albums)
                    _totalReleaseAmount.postValue(albums.size)
                    _peerAmount.postValue(musicCommunity.getPeers().size)
                    log("Manual refresh complete - releases: ${_releases.value?.size}, total: ${_totalReleaseAmount.value}, peers: ${_peerAmount.value}")
                } catch (e: Exception) {
                    log("Error in manual refresh: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            log("Cleaning up observers...")
            // Clean up the observeForever
            albumsFlowObserver?.let { observer ->
                albumRepository.getAlbumsFlow(musicCommunity.publicKeyHex()).removeObserver(observer)
            }
        }
    }
