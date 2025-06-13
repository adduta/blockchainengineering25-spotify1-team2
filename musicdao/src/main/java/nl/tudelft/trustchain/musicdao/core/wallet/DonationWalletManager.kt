package nl.tudelft.trustchain.musicdao.core.wallet

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.PeerAddress
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Date
import javax.inject.Inject

const val REG_TEST_FAUCET_IP = "131.180.27.224"

class DonationWalletManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val config: WalletConfig,
        private val artistRepository: ArtistRepository
    ) {
        var globalDonationAddress: String = ""
        var globalDonationBalance: Coin? = null
        var progress: Int = 0
        var isDownloading: Boolean = true
        private lateinit var walletKit: WalletAppKit
        private var lotteryJob: Job? = null
        private lateinit var walletService: WalletService
        private var allListenCounts: MutableMap<String, Int> = mutableMapOf()
        var lastLotteryTimestamp: Long = 0

        val onSetupCompletedListeners = mutableListOf<() -> Unit>()

        fun addOnSetupCompletedListener(listener: () -> Unit) {
            onSetupCompletedListeners.add(listener)
        }

        suspend fun start() =
            withContext(Dispatchers.IO) {
                // Ensure directory exists
                config.cacheDir.mkdirs()

                walletKit =
                    object : WalletAppKit(config.networkParams, config.cacheDir, config.filePrefix) {
                        override fun onSetupCompleted() {
                            // Make a fresh new key if no keys in stored wallet.
                            if (wallet().keyChainGroupSize < 1) {
                                Log.i("DonationWallet", "DonationWallet: Added manually created fresh key")
                                wallet().importKey(ECKey())
                            }
                            wallet().allowSpendingUnconfirmedTransactions()
                            Log.i("DonationWallet", "DonationWallet: DonationWallet started successfully.")
                            onSetupCompletedListeners.forEach {
                                Log.i("DonationWallet", "DonationWallet: calling listener $it")
                                it()
                            }
                        }
                    }

                if (config.networkParams == RegTestParams.get()) {
                    try {
                        val localHost = InetAddress.getByName(REG_TEST_FAUCET_IP)
                        walletKit.setPeerNodes(PeerAddress(config.networkParams, localHost, config.networkParams.port))
                    } catch (e: UnknownHostException) {
                        throw RuntimeException(e)
                    }
                }

                walletKit.setDownloadListener(
                    object : DownloadProgressTracker() {
                        override fun progress(
                            pct: Double,
                            blocksSoFar: Int,
                            date: Date?
                        ) {
                            super.progress(pct, blocksSoFar, date)
                            val percentage = pct.toInt()
                            progress = percentage
                            Log.i("DonationWallet", "Progress: $percentage")
                        }

                        override fun doneDownload() {
                            super.doneDownload()
                            progress = 100
                            Log.i("DonationWallet", "DonationWallet Download Complete!")
                            Log.i("DonationWallet", "DonationWallet Balance: ${walletKit.wallet().balance}")
                            isDownloading = false
                        }
                    }
                )

                walletKit.setBlockingStartup(false)
                    .startAsync()
                    .awaitRunning()

                walletService = WalletService(config, walletKit)

                Log.d("DonationWallet", "Started with address: ${getDonationAddress()}")
            }

        fun getDonationAddress(): String {
            this.globalDonationAddress = walletKit.wallet().issuedReceiveAddresses[0].toString()
            return walletKit.wallet().issuedReceiveAddresses[0].toString()
        }

        fun getBalance(): Coin {
            this.globalDonationBalance = walletKit.wallet().balance
            return walletKit.wallet().balance
        }

        fun runLottery() {
            if (!::walletKit.isInitialized) {
                Log.e("DonationWalletLottery", "Cannot start lottery: Wallet not initialized")
                return
            }
            try {
                if (!walletKit.isRunning || walletKit.wallet() == null) {
                    Log.d("DonationWalletLottery", "Waiting for wallet to be ready...")
                    return
                }

                // Get current balance
                val balance = walletService.confirmedBalance()
                if (balance == null) {
                    Log.i("DonationWalletLottery", "The balance is null for distribution")
                    return
                }

                Log.e("DonationWalletLottery", "Current balance is ${balance.toPlainString()}")

                val peerGroup = walletKit.peerGroup()
                val pendingTxs = walletKit.wallet().pendingTransactions
                Log.d("DonationWalletLottery", "Number of pending transactions ${pendingTxs.size}")
                for (tx in pendingTxs) {
                    peerGroup.broadcastTransaction(tx)
                }

                // Get all artists
                val artists = artistRepository.getArtists()
                if (artists.isEmpty()) {
                    Log.i("DonationWalletLottery", "No artists found to distribute donations")
                    return
                }

                val addressStringList = mutableListOf<String>()
                artists.forEach { artist -> addressStringList.add(artist.bitcoinAddress) }

                val target = balance.value

                // Calculate amount per artist (1/n of total balance)
                // val amountPerArtist = balance.divide(artists.size.toLong()).divide(2)

                val result = walletService.createBatchSpendExact(addressStringList, target)
                Log.i("DonationWalletLottery", "Each artist receives: ${result.second}")
                Log.i("DonationWalletLottery", "Money distributed to artists without fee: ${result.second * artists.size}")

                walletService.sendTransaction(result.first)
            } catch (e: Exception) {
                Log.e("DonationWalletLottery", "Error in lottery distribution: ${e.message}")
            }
        }

        suspend fun runWeightedLottery(listenCounts: Map<String, Int>) {
            if (!::walletKit.isInitialized) {
                Log.e("DonationWalletLottery", "Cannot start lottery: Wallet not initialized")
                return
            }
            try {
                if (!walletKit.isRunning || walletKit.wallet() == null) {
                    Log.d("DonationWalletLottery", "Waiting for wallet to be ready...")
                    return
                }

                for ((key, value) in listenCounts) {
                    val bitcoinAddress = artistRepository.getArtist(key)?.bitcoinAddress as String
                    this.allListenCounts[bitcoinAddress] = this.allListenCounts.getOrDefault(bitcoinAddress, 0) + value
                }

                // Get current balance
                val balance = walletService.confirmedBalance()
                if (balance == null) {
                    Log.i("DonationWalletLottery", "The balance is null for distribution")
                    return
                }

                Log.e("DonationWalletLottery", "Current balance is ${balance.toPlainString()}")

                val peerGroup = walletKit.peerGroup()
                val pendingTxs = walletKit.wallet().pendingTransactions
                Log.d("DonationWalletLottery", "Number of pending transactions ${pendingTxs.size}")
                for (tx in pendingTxs) {
                    peerGroup.broadcastTransaction(tx)
                }

                if (this.allListenCounts.values.all { it == 0 }) {
                    Log.i("DonationWalletLottery", "No artist found with non zero weight")
                    return
                }

                val target = balance.value

                // Calculate amount per artist (1/n of total balance)
                // val amountPerArtist = balance.divide(artists.size.toLong()).divide(2)

                val (tx, paidArtists, skippedArtists) =
                    walletService.createBatchSpendExactWeighted(
                        this.allListenCounts.toMutableMap(),
                        target
                    )
                Log.i("DonationWalletLottery", "Paid artists: $paidArtists")
                Log.i("DonationWalletLottery", "Skipped artists: $skippedArtists")

                paidArtists.keys.forEach { artist ->
                    this.allListenCounts[artist] = 0
                }

                walletService.sendTransaction(tx)
            } catch (e: Exception) {
                Log.e("DonationWalletLottery", "Error in lottery distribution: ${e.message}")
            }
        }

        // Stop method to clean up resources
        fun stop() {
            lotteryJob?.cancel()
            lotteryJob = null
            if (::walletKit.isInitialized) {
                walletKit.stopAsync() // Stop the wallet kit
                walletKit.awaitTerminated() // Wait for it to terminate
                Log.d("DonationWallet", "DonationWallet stopped successfully.")
            } else {
                Log.w("DonationWallet", "Attempted to stop DonationWallet, but it was not initialized.")
            }
        }
    }
