package nl.tudelft.trustchain.musicdao.core.wallet

import android.content.Context
import io.mockk.*
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.Wallet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class DonationWalletManagerTest {
    private lateinit var context: Context
    private lateinit var config: WalletConfig
    private lateinit var artistRepository: ArtistRepository
    private lateinit var walletKit: WalletAppKit
    private lateinit var wallet: Wallet
    private lateinit var walletService: WalletService

    private lateinit var donationWalletManager: DonationWalletManager

    @BeforeEach
    fun setUp() {
        context = mockk()
        config = mockk()
        artistRepository = mockk()
        walletKit = mockk()
        wallet = mockk()
        walletService = mockk(relaxed = true) // this replaces `just Runs`

        every { config.cacheDir } returns mockk<File>()

        donationWalletManager = DonationWalletManager(context, config, artistRepository)

        val walletKitField = donationWalletManager::class.java.getDeclaredField("walletKit")
        walletKitField.isAccessible = true
        walletKitField.set(donationWalletManager, walletKit)

        val walletServiceField = donationWalletManager::class.java.getDeclaredField("walletService")
        walletServiceField.isAccessible = true
        walletServiceField.set(donationWalletManager, walletService)

        every { walletKit.wallet() } returns wallet
    }

    @Test
    fun `getDonationAddress should return valid address`() {
        // Arrange
        val mockAddress = mockk<Address>()
        every { mockAddress.toString() } returns "testAddress"

        every { wallet.issuedReceiveAddresses } returns listOf(mockAddress)
        every { walletKit.wallet() } returns wallet

        // Act
        val address = donationWalletManager.getDonationAddress()

        // Assert
        assertEquals("testAddress", address)
        assertEquals("testAddress", donationWalletManager.globalDonationAddress)
    }

    @Test
    fun `getBalance should return correct value`() {
        val expectedBalance = Coin.valueOf(50000)
        every { wallet.balance } returns expectedBalance

        val balance = donationWalletManager.getBalance()

        assertEquals(expectedBalance, balance)
        assertEquals(expectedBalance, donationWalletManager.globalDonationBalance)
    }

    @Test
    fun `runLottery should not proceed if wallet not running`() {
        every { walletKit.isRunning } returns false

        donationWalletManager.runLottery()

        verify(exactly = 0) { walletService.confirmedBalance() }
    }

    @Test
    fun `runLottery should not proceed if balance is null`() {
        every { walletKit.isRunning } returns true
        every { walletService.confirmedBalance() } returns null

        donationWalletManager.runLottery()

        verify(exactly = 0) { artistRepository.getArtists() }
    }

    @Test
    fun `runLottery should not proceed if no artists`() {
        every { walletKit.isRunning } returns true
        every { walletService.confirmedBalance() } returns Coin.COIN
        every { wallet.pendingTransactions } returns emptyList()
        every { walletKit.peerGroup() } returns mockk()
        every { artistRepository.getArtists() } returns emptyList()

        donationWalletManager.runLottery()

        verify(exactly = 0) { walletService.createBatchSpendExact(any(), any()) }
    }

    @Test
    fun `runLottery should distribute funds to artists`() {
        every { walletKit.isRunning } returns true
        every { walletService.confirmedBalance() } returns Coin.valueOf(100000)
        every { wallet.pendingTransactions } returns emptyList()
        every { walletKit.peerGroup() } returns mockk()

        val artists =
            listOf(
                Artist("pubKey1", "addr1", "Artist One", "bio1", "socials1", listOf()),
                Artist("pubKey2", "addr2", "Artist Two", "bio2", "socials2", listOf())
            )

        every { artistRepository.getArtists() } returns artists
        val tx = mockk<Transaction>()
        every {
            walletService.createBatchSpendExact(listOf("addr1", "addr2"), 100000)
        } returns Pair(tx, 50000)

        donationWalletManager.runLottery()

        verify {
            walletService.createBatchSpendExact(listOf("addr1", "addr2"), 100000)
            walletService.sendTransaction(tx)
        }
    }

    @Test
    fun `stop should clean up resources`() {
        every { walletKit.stopAsync() } returns walletKit
        every { walletKit.awaitTerminated() } returns Unit

        donationWalletManager.stop()

        verify {
            walletKit.stopAsync()
            walletKit.awaitTerminated()
        }
    }
}
