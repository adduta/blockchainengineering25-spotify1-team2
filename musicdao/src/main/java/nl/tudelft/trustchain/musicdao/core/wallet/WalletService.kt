package nl.tudelft.trustchain.musicdao.core.wallet
import android.R.string
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.util.Date
import java.util.Dictionary


class WalletService(val config: WalletConfig, private val app: WalletAppKit) {
    private var started = false
    private var percentageSynced = 0

    val userTransactions: MutableStateFlow<List<UserWalletTransaction>> = MutableStateFlow(listOf())
    val onSetupCompletedListeners = mutableListOf<() -> Unit>()

    fun addOnSetupCompletedListener(listener: () -> Unit) {
        onSetupCompletedListeners.add(listener)
    }

    init {
        config.cacheDir.mkdirs()

        app.setDownloadListener(
            object : DownloadProgressTracker() {
                override fun progress(
                    pct: Double,
                    blocksSoFar: Int,
                    date: Date?
                ) {
                    super.progress(pct, blocksSoFar, date)
                    val percentage = pct.toInt()
                    percentageSynced = percentage
                    Log.i("MusicDao2", "Progress: $percentage")
                }

                override fun doneDownload() {
                    super.doneDownload()
                    percentageSynced = 100
                    Log.d("MusicDao2", "Download Complete!")
                    Log.d("MusicDao2", "Balance: ${app.wallet().balance}")
                }
            }
        )
        started = true
    }

    fun wallet(): Wallet {
        return app.wallet()
    }

    fun isStarted(): Boolean {
        return started && app.wallet() != null
    }

    /**
     * Convert an amount of coins represented by a user input string, and then send it
     * @param coinsAmount the amount of coins to send, as a string, such as "5", "0.5"
     * @param publicKey the public key address of the cryptocurrency wallet to send the funds to
     */
    fun sendCoins(
        publicKey: String,
        coinsAmount: String
    ): Boolean {
        Log.d("MusicDao", "Wallet (1): sending $coinsAmount to $publicKey")

        val coins: BigDecimal =
            try {
                BigDecimal(coinsAmount.toDouble())
            } catch (e: NumberFormatException) {
                Log.d("MusicDao", "Wallet (2): failed to parse $coinsAmount")
                null
            } ?: return false

        val satoshiAmount = (coins * SATS_PER_BITCOIN).toLong()

        val targetAddress: Address =
            try {
                Address.fromString(config.networkParams, publicKey)
            } catch (e: Exception) {
                Log.d("MusicDao", "Wallet (3): failed to parse $publicKey")
                null
            } ?: return false

        val sendRequest = SendRequest.to(targetAddress, Coin.valueOf(satoshiAmount))

        return try {
            app.wallet().sendCoins(sendRequest)
            Log.d("MusicDao", "Wallet (2): successfully sent $coinsAmount to $publicKey")
            true
        } catch (e: Exception) {
            Log.d("MusicDao", "Wallet (3): failed sending $coinsAmount to $publicKey")
            false
        }
    }

    fun sendBatchTransaction(recipients: List<Pair<String, String>>): Boolean {
        val tx = buildBatchTransaction(recipients)
        return sendTransaction(tx)
    }

    fun buildBatchTransaction(recipients: List<Pair<String, String>>): Transaction {
        val tx = Transaction(config.networkParams)
        for ((publicKey, coinsAmount) in recipients) {
            val coins =
                try {
                    BigDecimal(coinsAmount.toDouble())
                } catch (e: NumberFormatException) {
                    Log.d("MusicDao", "Wallet (2): failed to parse $coinsAmount")
                    continue
                }
            val satoshiAmount = (coins * SATS_PER_BITCOIN).toLong()
            val targetAddress =
                try {
                    Address.fromString(config.networkParams, publicKey)
                } catch (e: Exception) {
                    Log.d("MusicDao", "Wallet (3): failed to parse $publicKey")
                    continue
                }
            tx.addOutput(Coin.valueOf(satoshiAmount), targetAddress)
        }
        return tx
    }

    fun estimateFee(tx: Transaction): Long {
        val request = SendRequest.forTx(tx)
        wallet().completeTx(request)
        val feePaid: Long = request.tx.getFee().value
        return feePaid
    }

    fun sendTransaction(tx: Transaction): Boolean {
        val sendRequest = SendRequest.forTx(tx)
        return try {
            app.wallet().sendCoins(sendRequest)
            Log.d("MusicDao", "Wallet (2): successfully sent batch transaction")
            true
        } catch (e: Exception) {
            Log.d("MusicDao", "Wallet (3): failed sending batch transaction $e")
            false
        }
    }

    fun buildAddressList(addressStringList: List<String>): List<Address> {
        val addressList = mutableListOf<Address>()
        for (publicKey in addressStringList) {
            val targetAddress =
                try {
                    Address.fromString(config.networkParams, publicKey)
                } catch (e: Exception) {
                    Log.d("MusicDao", "Wallet (3): failed to parse $publicKey")
                    continue
                }
            addressList.add(targetAddress)
        }
        return addressList
    }

    fun createBatchSpendExact(
        addressStringList: List<String>,
        target: Long,
        minPerRecipient: Long = 5000L,
        feeBuffer: Long = 3000L
    ): Pair<Transaction, Long> {
        require(addressStringList.isNotEmpty()) { "Recipient list must not be empty." }
        val addressList = buildAddressList(addressStringList)
        val size = addressList.size

        // Binary search for the max payout per address such that sum + fee <= target
        var left = minPerRecipient
        var right = target / size
        var bestAmount = -1L

        while (left <= right) {
            val mid = (left + right) / 2
            val tx = Transaction(config.networkParams)
            addressList.forEach { address -> tx.addOutput(Coin.valueOf(mid), address) }
            try {
                val fee = estimateFee(tx)
                val totalNeeded = mid * size + fee

                if (totalNeeded <= target - feeBuffer) {
                    bestAmount = mid // So far, this works!
                    left = mid + 1 // Try to pay more per person
                } else {
                    right = mid - 1 // Too expensive, pay less
                }
            } catch (e: Exception) {
                // Log.e("DonationWalletLottery", "Error in estimating money: $e")
                right = mid - 1
            }
        }

        if (bestAmount < minPerRecipient) {
            throw IllegalArgumentException(
                "Cannot create batch tx: amount per recipient ($bestAmount) too low for $size recipients with total $target."
            )
        }

        // Build the final transaction with the best amount found
        val finalTx = Transaction(config.networkParams)
        addressList.forEach { address -> finalTx.addOutput(Coin.valueOf(bestAmount), address) }
        return Pair(finalTx, bestAmount)
    }

    fun createBatchSpendExactWeighted(
        listenCounts: Map<String, Int>,
        target: Long,
        minPerRecipient: Long = 5000L,
        feeBuffer: Long = 3000L
    ): Triple<Transaction, Map<String, Long>, Map<String, Long>> {
        val totalWeight = listenCounts.values.sum()
        Log.d("DonationWalletLottery", "Total weight: $totalWeight")

        var payouts =
            listenCounts.mapValues { (_, count) ->
                ((target * count.toDouble()) / totalWeight).toLong()
            }
        Log.d("DonationWalletLottery", "Initial payouts: $payouts")

        while (true) {
            // Filter out artists whose payout is too small
            val (valid, invalid) = payouts.entries.partition { it.value >= minPerRecipient }
            val payoutsFiltered = valid.associate { it.toPair() }
            val skipped = invalid.associate { it.toPair() }
            Log.d("DonationWalletLottery", "Valid payouts: $payoutsFiltered")
            Log.d("DonationWalletLottery", "Skipped payouts (too low): $skipped")

            try {
                val tx = Transaction(config.networkParams)
                for ((artist, amount) in payoutsFiltered) {
                    val address = Address.fromString(config.networkParams, artist)
                    tx.addOutput(Coin.valueOf(amount), address)
                }

                val fee = estimateFee(tx)
                Log.d("DonationWalletLottery", "Estimated fee: $fee")

                val finalTx = Transaction(config.networkParams)
                for ((artist, amount) in payoutsFiltered) {
                    val address = Address.fromString(config.networkParams, artist)
                    finalTx.addOutput(Coin.valueOf(amount), address)
                }
                return Triple(finalTx, payoutsFiltered, skipped)
            } catch (e: Exception) {
                Log.d(
                    "DonationWalletLottery",
                    "Fee estimation failed, reducing payouts and retrying: $e"
                )
                payouts =
                    payouts.mapValues { (_, amount) ->
                        (amount - 100000).coerceAtLeast(0) // prevent negative values
                    }
            }
        }
    }

    fun createBatchSpendExactWeightedBetter(
        listenCounts: Map<String, Int>,
        target: Long,
        minPerRecipient: Long = 5000L,
        feeBuffer: Long = 3000L
    ): Triple<Transaction, Map<String, Long>, Map<String, Long>> {
        val artistList = listenCounts.entries
            .sortedByDescending { it.value }
            .map { it.toPair() }
        var leftArtists = 1
        var rightArtists = listenCounts.size
        var bestArtists = 0 to 0L
        while (leftArtists <= rightArtists) {
            val midArtists = (leftArtists + rightArtists) / 2
            val selectedArtists = artistList.take(midArtists)
            val weight = selectedArtists.sumOf { it.second }
            val payouts = selectedArtists.map { (artist, count) ->
                val payout = ((target.toDouble() * count) / weight).toLong()
                artist to payout
            }
            var leftReduce = 0L
            var bestReduce = -1L
            var rightReduce = (payouts.last().second - minPerRecipient)
            while (leftReduce <= rightReduce) {
                val midReduce = (leftReduce + rightReduce) / 2
                val payoutsReduce = payouts.map { (artist, payout) ->
                    artist to payout - midReduce
                }
                val tx = Transaction(config.networkParams)
                var total = 0L
                for ((artist, payout) in payoutsReduce){
                    val address = Address.fromString(config.networkParams, artist)
                    tx.addOutput(Coin.valueOf(payout), address)
                    total += payout
                }
                try {
                    val fee = estimateFee(tx)
                    val totalNeeded = total + fee

                    if (totalNeeded <= target - feeBuffer) {
                        bestReduce = midReduce
                        rightReduce = midReduce - 1
                    } else {
                        leftReduce = midReduce + 1
                    }
                } catch (e: Exception) {
                    leftReduce = midReduce + 1
                }
            }
            if (bestReduce == -1L){
                rightArtists = midArtists - 1
            }
            else {
                bestArtists = midArtists to bestReduce
                leftArtists = midArtists + 1
            }
        }
        if (bestArtists == 0 to 0L){
            throw IllegalArgumentException(
                "Cannot create a transaction: there is not enough money to pay any artist"
            )
        }
        val paidNumber = bestArtists.first
        val reducedValue = bestArtists.second
        val selectedArtists = artistList.take(paidNumber)
        val weight = selectedArtists.sumOf { it.second }
        val payouts = selectedArtists.map { (artist, count) ->
            val payout = ((target.toDouble() * count) / weight).toLong()
            artist to payout
        }
        val tx = Transaction(config.networkParams)
        val payoutsReduce = payouts.map { (artist, payout) ->
            artist to payout - reducedValue
        }
        for ((artist, payout) in payoutsReduce){
            val address = Address.fromString(config.networkParams, artist)
            tx.addOutput(Coin.valueOf(payout), address)
        }

        val payedArtist = mutableMapOf<String, Long>()
        val skippedArtist = mutableMapOf<String, Long>()
        artistList.forEachIndexed { index, (artist, value) ->
            if (index < paidNumber) {
                payedArtist[artist] = value.toLong()
            } else {
                skippedArtist[artist] = value.toLong()
            }
        }
        return Triple(tx, payedArtist, skippedArtist)
    }


    /**
     * Query the faucet to the default protocol address
     * @return whether request was successfully or not
     */
    suspend fun defaultFaucetRequest(): Boolean {
        return requestFaucet(protocolAddress().toString())
    }

    /**
     * Query the bitcoin faucet for some starter bitcoins
     * @param address the address to send the coins to
     * @return whether request was successfully or not
     */
    private suspend fun requestFaucet(address: String): Boolean {
        Log.d("MusicDao", "requestFaucet (1): $address")
        val obj = URL("${config.regtestFaucetEndPoint}/addBTC?address=$address")

        return withContext(Dispatchers.IO) {
            try {
                val con: InputStream? = obj.openStream()
                con?.close()
                Log.d(
                    "MusicDao",
                    "requestFaucet (2): $address using ${config.regtestFaucetEndPoint}/addBTC?address=$address"
                )
                true
            } catch (exception: IOException) {
                exception.printStackTrace()
                Log.d(
                    "MusicDao",
                    "requestFaucet failed (3): $address using ${config.regtestFaucetEndPoint}/addBTC?address=$address"
                )
                Log.d("MusicDao", "requestFaucet failed (4): $exception")
                false
            }
        }
    }

    fun walletStatus(): String {
        return app.state().name
    }

    fun percentageSynced(): Int {
        return percentageSynced
    }

    /**
     * @return default address used for all interactions on chain
     */
    fun protocolAddress(): Address {
        return app.wallet().issuedReceiveAddresses[0]
    }

    fun confirmedBalance(): Coin? {
        return try {
            app.wallet().balance
        } catch (e: java.lang.Exception) {
            null
        }
    }

    fun walletTransactions(): List<UserWalletTransaction> {
        return app.wallet().walletTransactions.map {
            UserWalletTransaction(
                transaction = it.transaction,
                value = it.transaction.getValue(app.wallet()),
                date = it.transaction.updateTime
            )
        }.sortedByDescending { it.date }
    }

    fun setWalletReceiveListener() {
        userTransactions.value = walletTransactions()
        app.wallet().addCoinsReceivedEventListener { _, _, _, _ ->
            userTransactions.value = walletTransactions()
        }
    }

    fun estimatedBalance(): String? {
        return try {
            app.wallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString()
        } catch (e: java.lang.Exception) {
            null
        }
    }

    companion object {
        val SATS_PER_BITCOIN = BigDecimal(100_000_000)
    }
}

data class UserWalletTransaction(
    val transaction: Transaction,
    val value: Coin,
    val date: Date
)
