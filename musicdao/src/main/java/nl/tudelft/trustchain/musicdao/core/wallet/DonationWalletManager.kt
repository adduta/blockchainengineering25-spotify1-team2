package nl.tudelft.trustchain.musicdao.core.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.concurrent.atomic.*
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.wallet.*
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.*
import kotlinx.coroutines.*
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist

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
            return walletKit.wallet().currentReceiveAddress().toString()
        }

        fun getBalance(): Coin {
            return walletKit.wallet().balance
        }


        fun startLottery() {
            if (!::walletKit.isInitialized) {
                Log.e("DonationWallet", "Cannot start lottery: Wallet not initialized")
                return
            }

            lotteryJob?.cancel() // Cancel any existing lottery job

            lotteryJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        // Request money from faucet
                        if (!walletKit.isRunning || walletKit.wallet() == null) {
                            Log.d("DonationWallet", "Waiting for wallet to be ready...")
                            delay(10000)
                            continue
                        }

                        // Get current balance
                        val balance = walletService.confirmedBalance()
                        if (balance == null ) {
                            Log.i("DonationWallet", "The balance is null for distribution")
                            delay(10000) // Wait 10 seconds before next attempt
                            continue
                        }

                        if(balance.isZero) {
                            Log.i("DonationWallet", "Balance is zero requesting money from faucet")
                            val faucetResult = walletService.defaultFaucetRequest()

                            if (faucetResult) {
                                Log.i("DonationWallet", "Successfully requested money from faucet")
                            } else {
                                Log.e("DonationWallet", "Failed to request money from faucet")
                            }
                            delay(1000)
                            continue
                        }

                        Log.e("DonationWallet", "Current balance is ${balance.toFriendlyString()}")

                        // Get all artists
                        val artists = artistRepository.getArtists()
                        if (artists.isEmpty()) {
                            Log.i("DonationWallet", "No artists found to distribute donations")
                            delay(10000) // Wait 10 seconds before next attempt
                            continue
                        }

                        // Calculate amount per artist (1/n of total balance)
                        val amountPerArtist = balance.divide(artists.size.toLong())
                        Log.i("DonationWallet", "Distributing ${amountPerArtist.toFriendlyString()} to each artist")

//                         Send to each artist
                         artists.forEach { artist ->
                             try {
                                 val result = walletService.sendCoins(artist.bitcoinAddress, amountPerArtist.toPlainString())
                                 if (result) {
                                     Log.i("TestArtist", "Successfully sent ${amountPerArtist.toFriendlyString()} to ${artist.name} (${artist.bitcoinAddress})")
                                 } else {
                                     Log.e("TestArtist", "Failed to send coins to ${artist.name} (${artist.bitcoinAddress})")
                                 }
                             } catch (e: Exception) {
                                 Log.e("DonationWallet", "Error sending coins to ${artist.name}: ${e.message}")
                             }
                         }

//                        val artist3173 = artists.find { it.name == "Artist 3173" }
//                        if (artist3173 != null) {
//                            Log.i("CheckArtist", "Found Artist 3173 with address: ${artist3173.bitcoinAddress}")
//                            val result = walletService.sendCoins(artist3173.bitcoinAddress, amountPerArtist.toPlainString())
//                            if (result) {
//                                Log.i("DonationWallet", "Successfully sent ${amountPerArtist.toFriendlyString()} to ${artist3173.name} (${artist3173.bitcoinAddress})")
//                            } else {
//                                Log.e("DonationWallet", "Failed to send coins to ${artist3173.name} (${artist3173.bitcoinAddress})")
//                            }
//                        } else {
//                            Log.w("CheckArtist", "Artist 3173 not found in artist list")
//                        }

                    } catch (e: Exception) {
                        Log.e("DonationWallet", "Error in lottery distribution: ${e.message}")
                    }

                    delay(10000) // Wait 10 seconds before next distribution
                }
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
