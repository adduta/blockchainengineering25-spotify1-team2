package nl.tudelft.trustchain.musicdao.core.wallet

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.bitcoinj.core.*
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletServiceTest {
    private lateinit var config: WalletConfig
    private lateinit var app: WalletAppKit
    private lateinit var wallet: Wallet
    private lateinit var walletService: WalletService

    @BeforeEach
    fun setUp() {
        config =
            mockk {
                every { cacheDir } returns mockk<File>(relaxed = true)
                every { networkParams } returns RegTestParams.get()
                every { regtestFaucetEndPoint } returns "http://localhost:8080"
            }

        wallet = mockk(relaxed = true)
        app =
            mockk(relaxed = true) {
                every { wallet() } returns wallet
            }

        walletService = spyk(WalletService(config, app))
    }

    @Test
    fun `sendCoins returns false for invalid amount`() {
        val result = walletService.sendCoins("somePublicKey", "invalid")
        assertFalse(result)
    }

    @Test
    fun `sendCoins returns false for invalid address`() {
        val result = walletService.sendCoins("!!invalidAddress!!", "1")
        assertFalse(result)
    }

    @Test
    fun `sendCoins returns true for valid input`() {
        mockkStatic(Address::class)
        mockkStatic(SendRequest::class)

        val mockAddress = mockk<Address>()
        val mockRequest = mockk<SendRequest>()

        every { Address.fromString(any(), any()) } returns mockAddress
        every { SendRequest.to(mockAddress, any()) } returns mockRequest
        every { wallet.sendCoins(mockRequest) } returns mockk()

        val result = walletService.sendCoins("validAddress", "1.0")

        assertTrue(result)
    }

    @Test
    fun `createBatchSpendExactWeightedBetter throws if not enough funds`() {
        val address = LegacyAddress.fromKey(config.networkParams, ECKey())
        val addressStr = address.toString()

        val listenCounts = mapOf(addressStr to 100)

        every { walletService.estimateFee(any()) } returns 10_000L

        assertFailsWith<IllegalArgumentException> {
            walletService.createBatchSpendExactWeightedBetter(
                listenCounts = listenCounts,
                target = 15_000L
            )
        }
    }

    @Test
    fun `sendTransaction returns true when sending works`() {
        mockkStatic(SendRequest::class)

        val tx = mockk<Transaction>()
        val request = mockk<SendRequest>()

        every { SendRequest.forTx(tx) } returns request
        every { wallet.sendCoins(request) } returns mockk()

        val result = walletService.sendTransaction(tx)

        assertTrue(result)
    }

    @Test
    fun `createBatchSpendExactWeightedBetter distributes funds proportionally`() {
        val addr1 = LegacyAddress.fromKey(RegTestParams.get(), ECKey())
        val addr2 = LegacyAddress.fromKey(RegTestParams.get(), ECKey())

        val listenCounts =
            mapOf(
                addr1.toString() to 10,
                addr2.toString() to 30
            )

        every { walletService.estimateFee(any()) } returns 1000L

        val total = 100_000L

        val (tx, paid, skipped) =
            walletService.createBatchSpendExactWeightedBetter(
                listenCounts = listenCounts,
                target = total
            )

        assertEquals(2, paid.size)
        assertEquals(0, skipped.size)
        assertEquals(2, tx.outputs.size)

        val payout1 = paid[addr1.toString()]!!
        val payout2 = paid[addr2.toString()]!!

        assertTrue(payout2 >= payout1 * 2)
    }

    @Test
    fun `sendTransaction returns false when sending fails`() {
        mockkStatic(SendRequest::class)

        val tx = mockk<Transaction>()
        val request = mockk<SendRequest>()

        every { SendRequest.forTx(tx) } returns request
        every { wallet.sendCoins(request) } throws RuntimeException("Boom")

        val result = walletService.sendTransaction(tx)

        assertFalse(result)
    }

    @Test
    fun `protocolAddress returns first issued address`() {
        val address = mockk<Address>()
        every { wallet.issuedReceiveAddresses } returns listOf(address)

        val result = walletService.protocolAddress()

        assertEquals(address, result)
    }

    @Test
    fun `confirmedBalance returns balance when available`() {
        val expected = Coin.COIN
        every { wallet.balance } returns expected

        val balance = walletService.confirmedBalance()

        assertEquals(expected, balance)
    }

    @Test
    fun `confirmedBalance returns null when exception occurs`() {
        every { wallet.balance } throws RuntimeException("error")

        val balance = walletService.confirmedBalance()

        assertNull(balance)
    }

    @Test
    fun `estimatedBalance returns string when available`() {
        every {
            wallet.getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString()
        } returns "1.0 BTC"

        val result = walletService.estimatedBalance()

        assertEquals("1.0 BTC", result)
    }

    @Test
    fun `estimatedBalance returns null on failure`() {
        every {
            wallet.getBalance(Wallet.BalanceType.ESTIMATED)
        } throws RuntimeException("Failure")

        val result = walletService.estimatedBalance()

        assertNull(result)
    }
}
