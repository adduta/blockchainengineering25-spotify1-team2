package nl.tudelft.trustchain.musicdao.ui.screens.profile

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.UserTierVerifier
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.core.model.AccountType
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.services.UserTierService
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class ProfileScreenViewModelTest {
    private lateinit var publicKey: String
    private lateinit var bitcoinWalletViewModel: BitcoinWalletViewModel
    private lateinit var artistRepository: ArtistRepository
    private lateinit var userTierService: UserTierService
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var userTierVerifier: UserTierVerifier
    private lateinit var albumRepository: AlbumRepository
    private lateinit var musicCommunity: MusicCommunity
    private lateinit var viewModel: ProfileScreenViewModel
    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        publicKey = "test_publickey"
        bitcoinWalletViewModel = mockk()
        artistRepository = mockk()
        userTierService = mockk()
        userTierBlockRepository = mockk()
        userTierVerifier = mockk()
        albumRepository = mockk()
        musicCommunity = mockk()

        every { musicCommunity.publicKeyHex() } returns "different_public_key"
        coEvery { artistRepository.getArtistStateFlow(publicKey = publicKey) } returns MutableStateFlow(null)
        coEvery { artistRepository.getArtistReleases(publicKey = publicKey) } returns emptyList()

        viewModel = ProfileScreenViewModel(
            publicKey = publicKey,
            bitcoinWalletViewModel = bitcoinWalletViewModel,
            artistRepository = artistRepository,
            userTierService = userTierService,
            userTierBlockRepository = userTierBlockRepository,
            userTierVerifier = userTierVerifier,
            albumRepository = albumRepository,
            musicCommunity = musicCommunity
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test isOwnProfile returns false for different public key`() {
        assertFalse(viewModel.isOwnProfile())
        verify { musicCommunity.publicKeyHex() }
    }

    @Test
    fun `test isOwnProfile returns true for matching public key`() {
        every { musicCommunity.publicKeyHex() } returns publicKey
        assertTrue(viewModel.isOwnProfile())
        verify { musicCommunity.publicKeyHex() }
    }

    @Test
    fun `test loadTierStatus sets BASIC tier when no valid tier blocks`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        every { userTierVerifier.isUltimateUser(any()) } returns false
        every { userTierVerifier.isProUser(any()) } returns false
        every { userTierBlockRepository.getBlocksForUser(any()) } returns emptyList()

        viewModel = ProfileScreenViewModel(
            publicKey = publicKey,
            bitcoinWalletViewModel = bitcoinWalletViewModel,
            artistRepository = artistRepository,
            userTierService = userTierService,
            userTierBlockRepository = userTierBlockRepository,
            userTierVerifier = userTierVerifier,
            albumRepository = albumRepository,
            musicCommunity = musicCommunity
        )

        assertEquals(AccountType.BASIC, viewModel.accountType.value)
        assertNull(viewModel.validUntil.value)
    }

    @Test
    fun `test loadTierStatus sets PRO tier when user is PRO`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        every { userTierVerifier.isUltimateUser(any()) } returns false
        every { userTierVerifier.isProUser(any()) } returns true

        val currentTime = System.currentTimeMillis()
        val validUntil = currentTime + (30L * 24L * 60L * 60L * 1000L)
        val block = UserTierBlock(
            userId = publicKey,
            tier = "PRO",
            validFrom = currentTime - 1000,
            validUntil = validUntil
        )
        every { userTierBlockRepository.getBlocksForUser(any()) } returns listOf(block)

        viewModel = ProfileScreenViewModel(
            publicKey = publicKey,
            bitcoinWalletViewModel = bitcoinWalletViewModel,
            artistRepository = artistRepository,
            userTierService = userTierService,
            userTierBlockRepository = userTierBlockRepository,
            userTierVerifier = userTierVerifier,
            albumRepository = albumRepository,
            musicCommunity = musicCommunity
        )

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.PRO, viewModel.accountType.value)
        assertEquals(Instant.ofEpochMilli(validUntil), viewModel.validUntil.value)
    }

    @Test
    fun `test loadTierStatus sets ULTIMATE tier when user is ULTIMATE`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        every { userTierVerifier.isUltimateUser(any()) } returns true
        every { userTierVerifier.isProUser(any()) } returns true

        val currentTime = System.currentTimeMillis()
        val validUntil = currentTime + (30L * 24L * 60L * 60L * 1000L)
        val block = UserTierBlock(
            userId = publicKey,
            tier = "ULTIMATE",
            validFrom = currentTime - 1000,
            validUntil = validUntil
        )

        every { userTierBlockRepository.getBlocksForUser(any()) } returns listOf(block)

        viewModel = ProfileScreenViewModel(
            publicKey = publicKey,
            bitcoinWalletViewModel = bitcoinWalletViewModel,
            artistRepository = artistRepository,
            userTierService = userTierService,
            userTierBlockRepository = userTierBlockRepository,
            userTierVerifier = userTierVerifier,
            albumRepository = albumRepository,
            musicCommunity = musicCommunity
        )

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.ULTIMATE, viewModel.accountType.value)
        assertEquals(Instant.ofEpochMilli(validUntil), viewModel.validUntil.value)
    }

    @Test
    fun `test upgradeToPro updates tier and validUntil on success`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        coEvery { userTierService.upgradeToPro(any(), any(), any()) } returns true
        coEvery { artistRepository.getArtistReleases(publicKey = publicKey) } returns emptyList()
        coEvery { albumRepository.refreshCache() } just Runs

        viewModel.upgradeToPro(months = 1)

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.PRO, viewModel.accountType.value)
        assertNotNull(viewModel.validUntil.value)
        coVerify {
            userTierService.upgradeToPro(
                userId = publicKey,
                durationMonths = 1,
                bitcoinWalletViewModel = bitcoinWalletViewModel
            )
            artistRepository.getArtistReleases(publicKey = publicKey)
            albumRepository.refreshCache()
        }
    }

    @Test
    fun `test upgradeToPro does not update tier on failure`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        coEvery { userTierService.upgradeToPro(any(), any(), any()) } returns false

        viewModel.upgradeToPro(months = 1)

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.BASIC, viewModel.accountType.value)
        assertNull(viewModel.validUntil.value)

        coVerify {
            userTierService.upgradeToPro(
                userId = publicKey,
                durationMonths = 1,
                bitcoinWalletViewModel = bitcoinWalletViewModel
            )
        }
    }

    @Test
    fun `test upgradeToUltimate updates tier and validUntil on success`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        coEvery { userTierService.upgradeToUltimate(any(), any(), any()) } returns true
        coEvery { artistRepository.getArtistReleases(publicKey = publicKey) } returns emptyList()
        coEvery { albumRepository.refreshCache() } just Runs

        viewModel.upgradeToUltimate(months = 1)

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.ULTIMATE, viewModel.accountType.value)
        assertNotNull(viewModel.validUntil.value)
        coVerify {
            userTierService.upgradeToUltimate(
                userId = publicKey,
                durationMonths = 1,
                bitcoinWalletViewModel = bitcoinWalletViewModel
            )
            artistRepository.getArtistReleases(publicKey = publicKey)
            albumRepository.refreshCache()
        }
    }

    @Test
    fun `test upgradeToUltimate does not update tier on failure`() = runTest {
        every { musicCommunity.publicKeyHex() } returns publicKey
        coEvery { userTierService.upgradeToUltimate(any(), any(), any()) } returns false

        viewModel.upgradeToUltimate(months = 1)

        // wait for coroutine to complete
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AccountType.BASIC, viewModel.accountType.value)
        assertNull(viewModel.validUntil.value)
        coVerify {
            userTierService.upgradeToUltimate(
                userId = publicKey,
                durationMonths = 1,
                bitcoinWalletViewModel = bitcoinWalletViewModel
            )
        }
    }
}
