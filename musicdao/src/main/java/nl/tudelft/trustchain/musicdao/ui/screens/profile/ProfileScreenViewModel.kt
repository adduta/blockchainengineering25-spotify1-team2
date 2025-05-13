package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileScreenViewModel
    @AssistedInject
    constructor(
        @Assisted private val publicKey: String,
        private val artistRepository: ArtistRepository,
        private val musicCommunity: MusicCommunity
    ) : ViewModel() {
        private val _profile: MutableStateFlow<Artist?> = MutableStateFlow(null)
        var profile: StateFlow<Artist?> = _profile

        private val _releases: MutableStateFlow<List<Album>> = MutableStateFlow(listOf())
        val releases: StateFlow<List<Album>> = _releases

        private val _isOwnProfile: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val isOwnProfile: StateFlow<Boolean> = _isOwnProfile

        init {
            viewModelScope.launch {
                profile = artistRepository.getArtistStateFlow(publicKey = publicKey)
                _releases.value = artistRepository.getArtistReleases(publicKey = publicKey)
                _isOwnProfile.value = publicKey == musicCommunity.publicKeyHex()
            }
        }

        @AssistedFactory
        interface ProfileScreenViewModelFactory {
            fun create(publicKey: String): ProfileScreenViewModel
        }

        companion object {
            fun provideFactory(
                assistedFactory: ProfileScreenViewModelFactory,
                publicKey: String
            ): ViewModelProvider.Factory =
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return assistedFactory.create(publicKey) as T
                    }
                }
        }
    }
