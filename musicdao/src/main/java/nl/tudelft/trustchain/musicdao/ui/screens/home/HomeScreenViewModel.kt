package nl.tudelft.trustchain.musicdao.ui.screens.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

        private var albumsFlowObserver: androidx.lifecycle.Observer<List<Album>>? = null

        init {
            setupAlbumsFlow()
            setupPeerUpdates()
        }

        private fun setupAlbumsFlow() {
            viewModelScope.launch {
                try {
                    val userPublicKey = musicCommunity.publicKeyHex()
                    
                    // Set initial peer count
                    _peerAmount.postValue(musicCommunity.getPeers().size)

                    // Set up the albums flow observer
                    val albumsFlow = albumRepository.getAlbumsFlow(userPublicKey)
                    albumsFlowObserver = androidx.lifecycle.Observer { albums ->
                        viewModelScope.launch {
                            refreshMagnetLinks(albums, userPublicKey)
                            _releases.postValue(albums)
                            _totalReleaseAmount.postValue(albums.size)
                        }
                    }
                    albumsFlow.observeForever(albumsFlowObserver!!)

                    // Initial load of albums
                    val initialAlbums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                    _releases.postValue(initialAlbums)
                    _totalReleaseAmount.postValue(initialAlbums.size)
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenViewModel", "Error in setupAlbumsFlow: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        private fun setupPeerUpdates() {
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(5000) // Update every 5 seconds
                    val currentPeers = musicCommunity.getPeers().size
                    if (currentPeers != _peerAmount.value) {
                        _peerAmount.postValue(currentPeers)
                    }
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
                    albums.forEach { album ->
                        // Check for various cases where we need to request a magnet link
                        if (album.magnet == null || 
                            album.magnet.isEmpty() || 
                            album.magnet.isBlank() || 
                            album.magnet == "access_restricted" ||
                            album.magnet == "null" ||
                            album.magnet == "undefined") {
                            try {
                                android.util.Log.d("HomeScreenViewModel", "Requesting magnet link for album ${album.id} (current magnet: ${album.magnet})")
                                albumRepository.requestMagnetLink(album.id)
                            } catch (e: Exception) {
                                android.util.Log.e("HomeScreenViewModel", "Error requesting magnet link for album ${album.id}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenViewModel", "Error refreshing magnet links: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                try {
                    val userPublicKey = musicCommunity.publicKeyHex()
                    val albums = albumRepository.getAlbums(userPublicKey, releaseRepository)
                    _releases.postValue(albums)
                    _totalReleaseAmount.postValue(albums.size)
                    _peerAmount.postValue(musicCommunity.getPeers().size)
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenViewModel", "Error in refresh: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            // Clean up the observeForever
            albumsFlowObserver?.let { observer ->
                albumRepository.getAlbumsFlow(musicCommunity.publicKeyHex()).removeObserver(observer)
            }
        }
    }
