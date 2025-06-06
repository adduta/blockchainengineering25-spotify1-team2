package nl.tudelft.trustchain.musicdao.core.services

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.bitcoinj.core.Coin
import kotlinx.coroutines.flow.MutableStateFlow

@DisplayName("UserTierService Tests")
class UserTierServiceTest {
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var bitcoinWalletViewModel: BitcoinWalletViewModel
    private lateinit var userTierService: UserTierService

    @BeforeEach
    fun setup() {
        userTierBlockRepository = mockk()
        bitcoinWalletViewModel = mockk()
        userTierService = UserTierService(userTierBlockRepository)
    }

    @Test
    @DisplayName("Should successfully upgrade to PRO tier with payment and block creation")
    fun `test upgradeToPro with successful payment and block creation`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            val durationMonths = 3
            val sufficientBalance = Coin.parseCoin("0.2") // More than required 0.1 BTC
            every { bitcoinWalletViewModel.confirmedBalance } returns MutableStateFlow(sufficientBalance)
            coEvery { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) } returns true
            coEvery {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "PRO",
                    validFrom = any(),
                    validUntil = any()
                )
            } returns mockk()

            // Act
            val result = userTierService.upgradeToPro(userId, durationMonths, bitcoinWalletViewModel)

            // Assert
            assertTrue(result)
            coVerify { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) }
            coVerify {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "PRO",
                    validFrom = any(),
                    validUntil = any()
                )
            }
        }

    @Test
    @DisplayName("Should fail to upgrade to PRO tier when payment fails")
    fun `test upgradeToPro with failed payment`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            val sufficientBalance = Coin.parseCoin("0.2") // More than required 0.1 BTC
            every { bitcoinWalletViewModel.confirmedBalance } returns MutableStateFlow(sufficientBalance)
            coEvery { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) } returns false

            // Act
            val result = userTierService.upgradeToPro(userId, null, bitcoinWalletViewModel)

            // Assert
            assertFalse(result)
            coVerify { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) }
            coVerify(exactly = 0) { userTierBlockRepository.create(any(), any(), any(), any()) }
        }

    @Test
    @DisplayName("Should fail to upgrade to PRO tier when block creation fails")
    fun `test upgradeToPro with successful payment but failed block creation`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            val sufficientBalance = Coin.parseCoin("0.2") // More than required 0.1 BTC
            every { bitcoinWalletViewModel.confirmedBalance } returns MutableStateFlow(sufficientBalance)
            coEvery { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) } returns true
            coEvery {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "PRO",
                    validFrom = any(),
                    validUntil = any()
                )
            } returns null

            // Act
            val result = userTierService.upgradeToPro(userId, null, bitcoinWalletViewModel)

            // Assert
            assertFalse(result)
            coVerify { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) }
            coVerify {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "PRO",
                    validFrom = any(),
                    validUntil = any()
                )
            }
        }

    @Test
    @DisplayName("Should fail to upgrade to PRO tier with insufficient balance")
    fun `test upgradeToPro with insufficient balance`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            val insufficientBalance = Coin.parseCoin("0.05") // Less than required 0.1 BTC
            every { bitcoinWalletViewModel.confirmedBalance } returns MutableStateFlow(insufficientBalance)

            // Act
            val result = userTierService.upgradeToPro(userId, null, bitcoinWalletViewModel)

            // Assert
            assertFalse(result)
            coVerify(exactly = 0) { bitcoinWalletViewModel.walletService.sendCoins(any(), any()) }
            coVerify(exactly = 0) { userTierBlockRepository.create(any(), any(), any(), any()) }
        }

    @Test
    @DisplayName("Should successfully downgrade to BASIC tier")
    fun `test downgradeToBasic with successful block creation`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            coEvery {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "BASIC",
                    validFrom = any(),
                    validUntil = null
                )
            } returns mockk()

            // Act
            val result = userTierService.downgradeToBasic(userId)

            // Assert
            assertTrue(result)
            coVerify {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "BASIC",
                    validFrom = any(),
                    validUntil = null
                )
            }
        }

    @Test
    @DisplayName("Should fail to downgrade to BASIC tier when block creation fails")
    fun `test downgradeToBasic with failed block creation`() =
        runBlocking {
            // Arrange
            val userId = "testUser123"
            coEvery {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "BASIC",
                    validFrom = any(),
                    validUntil = null
                )
            } returns null

            // Act
            val result = userTierService.downgradeToBasic(userId)

            // Assert
            assertFalse(result)
            coVerify {
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "BASIC",
                    validFrom = any(),
                    validUntil = null
                )
            }
        }
}
