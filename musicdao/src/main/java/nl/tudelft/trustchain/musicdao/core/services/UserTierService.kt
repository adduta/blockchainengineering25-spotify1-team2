package nl.tudelft.trustchain.musicdao.core.services

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import nl.tudelft.trustchain.musicdao.core.wallet.DonationWalletManager
import javax.inject.Inject
import android.util.Log
import org.bitcoinj.core.Coin

class UserTierService
    @Inject
    constructor(
        private val userTierBlockRepository: UserTierBlockRepository,
        private val donationWalletManager: DonationWalletManager
    ) {
        suspend fun upgradeToPro(
            userId: String,
            durationMonths: Int? = null,
            bitcoinWalletViewModel: BitcoinWalletViewModel
        ): Boolean {
            Log.i("UserTierService", "Attempting to upgrade user $userId to PRO tier")

            try {
                // Check wallet balance first
                val balance = bitcoinWalletViewModel.confirmedBalance.value
                if (balance == null) {
                    Log.e("UserTierService", "Failed to get wallet balance")
                    return false
                }

                val requiredAmount = Coin.parseCoin("0.1")
                if (balance.isLessThan(requiredAmount)) {
                    Log.e("UserTierService", "Insufficient balance. Required: $requiredAmount, Available: $balance")
                    return false
                }

                // Send payment
                Log.d("UserTierService", "Sending payment of 0.1 BTC")
                val paymentSuccess =
                    bitcoinWalletViewModel.walletService.sendCoins(
                        donationWalletManager.globalDonationAddress,
                        "0.1"
                    )

                if (!paymentSuccess) {
                    Log.e("UserTierService", "Failed to send coins for user $userId upgrade")
                    return false
                }

                // Wait a bit for the transaction to be processed
                kotlinx.coroutines.delay(2000)

                // Calculate validity period
                val currentTime = System.currentTimeMillis()
                val validUntil =
                    durationMonths?.let {
                        currentTime + (it * 30L * 24L * 60L * 60L * 1000L) // Convert months to milliseconds
                    }

                // Create the tier block
                Log.d("UserTierService", "Creating PRO tier block")
                val block =
                    userTierBlockRepository.create(
                        userId = userId,
                        tier = "PRO",
                        validFrom = currentTime,
                        validUntil = validUntil
                    )

                if (block == null) {
                    Log.e("UserTierService", "Failed to create PRO tier block for user $userId")
                    return false
                }

                Log.i("UserTierService", "Successfully upgraded user $userId to PRO tier")
                return true
            } catch (e: Exception) {
                Log.e("UserTierService", "Error during upgrade process: ${e.message}")
                e.printStackTrace()
                return false
            }
        }

        suspend fun upgradeToUltimate(
            userId: String,
            durationMonths: Int? = null,
            bitcoinWalletViewModel: BitcoinWalletViewModel
        ): Boolean {
            Log.i("UserTierService", "Attempting to upgrade user $userId to ULTIMATE tier")

            try {
                // Check wallet balance first
                val balance = bitcoinWalletViewModel.confirmedBalance.value
                if (balance == null) {
                    Log.e("UserTierService", "Failed to get wallet balance")
                    return false
                }

                val requiredAmount = Coin.parseCoin("0.15")
                if (balance.isLessThan(requiredAmount)) {
                    Log.e("UserTierService", "Insufficient balance. Required: $requiredAmount, Available: $balance")
                    return false
                }

                // Send payment
                Log.d("UserTierService", "Sending payment of 0.15 BTC")
                val paymentSuccess =
                    bitcoinWalletViewModel.walletService.sendCoins(
                        "mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub",
                        "0.15"
                    )

                if (!paymentSuccess) {
                    Log.e("UserTierService", "Failed to send coins for user $userId upgrade")
                    return false
                }

                // Wait a bit for the transaction to be processed
                kotlinx.coroutines.delay(2000)

                // Calculate validity period
                val currentTime = System.currentTimeMillis()
                val validUntil =
                    durationMonths?.let {
                        currentTime + (it * 30L * 24L * 60L * 60L * 1000L) // Convert months to milliseconds
                    }

                // Create the tier block
                Log.d("UserTierService", "Creating ULTIMATE tier block")
                val block =
                    userTierBlockRepository.create(
                        userId = userId,
                        tier = "ULTIMATE",
                        validFrom = currentTime,
                        validUntil = validUntil
                    )

                if (block == null) {
                    Log.e("UserTierService", "Failed to create ULTIMATE tier block for user $userId")
                    return false
                }

                Log.i("UserTierService", "Successfully upgraded user $userId to ULTIMATE tier")
                return true
            } catch (e: Exception) {
                Log.e("UserTierService", "Error during upgrade process: ${e.message}")
                e.printStackTrace()
                return false
            }
        }

        suspend fun downgradeToBasic(userId: String): Boolean {
            Log.i("UserTierService", "Attempting to downgrade user $userId to BASIC tier")
            val currentTime = System.currentTimeMillis()

            val block =
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "BASIC",
                    validFrom = currentTime,
                    validUntil = null
                )

            if (block == null) {
                Log.e("UserTierService", "Failed to create BASIC tier block for user $userId")
            } else {
                Log.i("UserTierService", "Successfully downgraded user $userId to BASIC tier")
            }

            return block != null
        }
    }
