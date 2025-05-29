package nl.tudelft.trustchain.musicdao.core.services

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import javax.inject.Inject
import android.util.Log

class UserTierService
    @Inject
    constructor(
        private val userTierBlockRepository: UserTierBlockRepository
    ) {
        suspend fun upgradeToPro(
            userId: String,
            durationMonths: Int? = null,
            bitcoinWalletViewModel: BitcoinWalletViewModel
        ): Boolean {
            Log.i("UserTierService", "Attempting to upgrade user $userId to PRO tier")

            // TODO(VianRobotin): Add PK of the wallet used for the lottery system.
            val res = bitcoinWalletViewModel.walletService.sendCoins("mmgibBwiPtcG91BDT9oD8VSSDhMZeLf2ub", "0.1")

            if (!res) {
                Log.e("UserTierService", "Failed to send coins for user $userId upgrade")
                return false
            }

            val currentTime = System.currentTimeMillis()
            val validUntil =
                durationMonths?.let {
                    currentTime + (it * 30L * 24L * 60L * 60L * 1000L) // Convert months to milliseconds
                }

            val block =
                userTierBlockRepository.create(
                    userId = userId,
                    tier = "PRO",
                    validFrom = currentTime,
                    validUntil = validUntil
                )

            if (block == null) {
                Log.e("UserTierService", "Failed to create PRO tier block for user $userId")
            } else {
                Log.i("UserTierService", "Successfully upgraded user $userId to PRO tier")
            }

            return block != null
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
