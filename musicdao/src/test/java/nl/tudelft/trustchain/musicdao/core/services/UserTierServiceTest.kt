package nl.tudelft.trustchain.musicdao.core.services

import io.mockk.*
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import org.bitcoinj.core.Coin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.runBlocking
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService

class UserTierServiceTest {
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var bitcoinWalletViewModel: BitcoinWalletViewModel
    private lateinit var walletService: WalletService
    private lateinit var service: UserTierService

    @BeforeEach
    fun setup() {
        userTierBlockRepository = mockk()
        bitcoinWalletViewModel = mockk()
        walletService = mockk()
        every { bitcoinWalletViewModel.walletService } returns walletService
        service = UserTierService(userTierBlockRepository)
    }

    @Test
    fun `test upgradeToPro fails when wallet balance is null`() = runBlocking {
        every { bitcoinWalletViewModel.confirmedBalance.value } returns null

        val result = service.upgradeToPro(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
    }

    @Test
    fun `test upgradeToPro fails when insufficient balance`() = runBlocking {
        val balance = Coin.parseCoin("0.05") // Less than required 0.1
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance

        val result = service.upgradeToPro(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
    }

    @Test
    fun `test upgradeToPro fails when payment fails`() = runBlocking {
        val balance = Coin.parseCoin("0.2") // More than required 0.1
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        } returns false

        val result = service.upgradeToPro(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        }
    }

    @Test
    fun `test upgradeToPro fails when block creation fails`() = runBlocking {
        val balance = Coin.parseCoin("0.2")
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        } returns true
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "PRO",
                validFrom = any(),
                validUntil = any()
            )
        } returns null

        val result = service.upgradeToPro(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        }
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "PRO",
                validFrom = any(),
                validUntil = any()
            )
        }
    }

    @Test
    fun `test upgradeToPro succeeds with valid parameters`() = runBlocking {
        val balance = Coin.parseCoin("0.2")
        val block = mockk<TrustChainBlock>()

        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        } returns true
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "PRO",
                validFrom = any(),
                validUntil = any()
            )
        } returns block

        val result = service.upgradeToPro(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertTrue(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.1"
            )
        }
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "PRO",
                validFrom = any(),
                validUntil = any()
            )
        }
    }

    @Test
    fun `test upgradeToUltimate fails when wallet balance is null`() = runBlocking {
        every { bitcoinWalletViewModel.confirmedBalance.value } returns null

        val result = service.upgradeToUltimate(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
    }

    @Test
    fun `test upgradeToUltimate fails when insufficient balance`() = runBlocking {
        val balance = Coin.parseCoin("0.1") // Less than required 0.15
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance

        val result = service.upgradeToUltimate(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
    }

    @Test
    fun `test upgradeToUltimate fails when payment fails`() = runBlocking {
        val balance = Coin.parseCoin("0.2") // More than required 0.15
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        } returns false

        val result = service.upgradeToUltimate(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        }
    }

    @Test
    fun `test upgradeToUltimate fails when block creation fails`() = runBlocking {
        val balance = Coin.parseCoin("0.2")
        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        } returns true
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = any(),
                validUntil = any()
            )
        } returns null

        val result = service.upgradeToUltimate(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertFalse(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        }
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = any(),
                validUntil = any()
            )
        }
    }

    @Test
    fun `test upgradeToUltimate succeeds with valid parameters`() = runBlocking {
        val balance = Coin.parseCoin("0.2")
        val block = mockk<TrustChainBlock>()

        every { bitcoinWalletViewModel.confirmedBalance.value } returns balance
        coEvery {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        } returns true
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = any(),
                validUntil = any()
            )
        } returns block

        val result = service.upgradeToUltimate(
            userId = "test_user",
            durationMonths = 1,
            bitcoinWalletViewModel = bitcoinWalletViewModel
        )

        assertTrue(result)
        verify { bitcoinWalletViewModel.confirmedBalance.value }
        coVerify {
            walletService.sendCoins(
                "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                "0.15"
            )
        }
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = any(),
                validUntil = any()
            )
        }
    }

    @Test
    fun `test downgradeToBasic fails when block creation fails`() = runBlocking {
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "BASIC",
                validFrom = any(),
                validUntil = null
            )
        } returns null

        val result = service.downgradeToBasic("test_user")

        assertFalse(result)
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "BASIC",
                validFrom = any(),
                validUntil = null
            )
        }
    }

    @Test
    fun `test downgradeToBasic succeeds with valid parameters`() = runBlocking {
        val block = mockk<TrustChainBlock>()
        coEvery {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "BASIC",
                validFrom = any(),
                validUntil = null
            )
        } returns block

        val result = service.downgradeToBasic("test_user")

        assertTrue(result)
        coVerify {
            userTierBlockRepository.create(
                userId = "test_user",
                tier = "BASIC",
                validFrom = any(),
                validUntil = null
            )
        }
    }
}
