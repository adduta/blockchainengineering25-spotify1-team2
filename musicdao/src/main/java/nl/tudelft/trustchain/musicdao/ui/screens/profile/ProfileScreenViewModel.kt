package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.model.AccountType
import nl.tudelft.trustchain.musicdao.core.services.UserTierService
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.core.ipv8.UserTierVerifier
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import java.time.Instant

class ProfileScreenViewModel
    @AssistedInject
    constructor(
        @Assisted private val publicKey: String,
        @Assisted private val bitcoinWalletViewModel: BitcoinWalletViewModel,
        private val artistRepository: ArtistRepository,
        private val userTierService: UserTierService,
        private val userTierBlockRepository: UserTierBlockRepository,
        private val userTierVerifier: UserTierVerifier,
        private val albumRepository: AlbumRepository,
        private val musicCommunity: MusicCommunity
    ) : ViewModel() {
        private val _profile: MutableStateFlow<Artist?> = MutableStateFlow(null)
        var profile: StateFlow<Artist?> = _profile

        private val _releases: MutableStateFlow<List<Album>> = MutableStateFlow(listOf())
        val releases: StateFlow<List<Album>> = _releases

        private val _accountType = MutableStateFlow(AccountType.BASIC)
        val accountType: StateFlow<AccountType> = _accountType

        private val _validUntil = MutableStateFlow<Instant?>(null)
        val validUntil: StateFlow<Instant?> = _validUntil

        fun isOwnProfile(): Boolean {
            return publicKey == musicCommunity.publicKeyHex()
        }

        init {
            viewModelScope.launch {
                profile = artistRepository.getArtistStateFlow(publicKey = publicKey)
                _releases.value = artistRepository.getArtistReleases(publicKey = publicKey)
            }
            // Only load tier status if this is the user's own profile
            if (isOwnProfile()) {
                loadTierStatus()
            }
        }

        private fun loadTierStatus() {
            viewModelScope.launch {
                val publicKeyBytes = publicKey.hexToBytes()
                val isUltimate = userTierVerifier.isUltimateUser(publicKeyBytes)
                val isPro = userTierVerifier.isProUser(publicKeyBytes)

                if (isUltimate) {
                    _accountType.value = AccountType.ULTIMATE
                } else if (isPro) {
                    _accountType.value = AccountType.PRO
                } else {
                    _accountType.value = AccountType.BASIC
                }

                // Get the most recent valid tier block to determine validity period
                val userTierBlocks = userTierBlockRepository.getBlocksForUser(publicKeyBytes)
                val currentTime = System.currentTimeMillis()
                val validTierBlock =
                    userTierBlocks
                        .filter { it.validFrom <= currentTime && (it.validUntil == null || it.validUntil > currentTime) }
                        .maxByOrNull { it.validFrom }

                _validUntil.value = validTierBlock?.validUntil?.let { Instant.ofEpochMilli(it) }
            }
        }

        fun upgradeToPro(months: Int = 1) {
            viewModelScope.launch {
                val success =
                    userTierService.upgradeToPro(
                        userId = publicKey,
                        durationMonths = months,
                        bitcoinWalletViewModel = bitcoinWalletViewModel
                    )

                if (success) {
                    _accountType.value = AccountType.PRO
                    // Calculate validUntil based on months
                    _validUntil.value = Instant.now().plusSeconds(months * 30L * 24L * 60L * 60L)
                    // Force refresh releases to get updated magnet links
                    _releases.value = artistRepository.getArtistReleases(publicKey = publicKey)
                    // Force refresh the cache to ensure magnet links are updated
                    albumRepository.refreshCache()
                }
            }
        }

        fun upgradeToUltimate(months: Int = 1) {
            viewModelScope.launch {
                val success =
                    userTierService.upgradeToUltimate(
                        userId = publicKey,
                        durationMonths = months,
                        bitcoinWalletViewModel = bitcoinWalletViewModel
                    )

                if (success) {
                    _accountType.value = AccountType.ULTIMATE
                    // Calculate validUntil based on months
                    _validUntil.value = Instant.now().plusSeconds(months * 30L * 24L * 60L * 60L)
                    // Force refresh releases to get updated magnet links
                    _releases.value = artistRepository.getArtistReleases(publicKey = publicKey)
                    // Force refresh the cache to ensure magnet links are updated
                    albumRepository.refreshCache()
                }
            }
        }

        @AssistedFactory
        interface ProfileScreenViewModelFactory {
            fun create(
                publicKey: String,
                bitcoinWalletViewModel: BitcoinWalletViewModel
            ): ProfileScreenViewModel
        }

        companion object {
            fun provideFactory(
                assistedFactory: ProfileScreenViewModelFactory,
                publicKey: String,
                bitcoinWalletViewModel: BitcoinWalletViewModel
            ): ViewModelProvider.Factory =
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return assistedFactory.create(publicKey, bitcoinWalletViewModel) as T
                    }
                }
        }
    }
