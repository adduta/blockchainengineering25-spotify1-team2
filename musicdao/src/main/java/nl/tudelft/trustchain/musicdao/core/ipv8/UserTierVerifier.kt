package nl.tudelft.trustchain.musicdao.core.ipv8

import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import javax.inject.Inject

class UserTierVerifier
    @Inject
    constructor(
        private val userTierBlockRepository: UserTierBlockRepository
    ) {
        fun isProUser(userPublicKey: ByteArray): Boolean {
            val userTierBlocks = userTierBlockRepository.getBlocksForUser(userPublicKey)

            // If there are no tier blocks, user is not Pro
            if (userTierBlocks.isEmpty()) {
                return false
            }

            // Get the most recent valid tier block
            val currentTime = System.currentTimeMillis()
            val validTierBlock =
                userTierBlocks
                    .filter { it.validFrom <= currentTime && (it.validUntil == null || it.validUntil > currentTime) }
                    .maxByOrNull { it.validFrom }

            // If there is no valid tier block, user is not Pro
            if (validTierBlock == null) {
                return false
            }

            // Both PRO and ULTIMATE users have access to PRO features
            return validTierBlock.tier == "PRO" || validTierBlock.tier == "ULTIMATE"
        }

        fun isUltimateUser(userPublicKey: ByteArray): Boolean {
            val userTierBlocks = userTierBlockRepository.getBlocksForUser(userPublicKey)

            // If there are no tier blocks, user is not Ultimate
            if (userTierBlocks.isEmpty()) {
                return false
            }

            // Get the most recent valid tier block
            val currentTime = System.currentTimeMillis()
            val validTierBlock =
                userTierBlocks
                    .filter { it.validFrom <= currentTime && (it.validUntil == null || it.validUntil > currentTime) }
                    .maxByOrNull { it.validFrom }

            // If there is no valid tier block, user is not Ultimate
            if (validTierBlock == null) {
                return false
            }

            Log.d("UserTierVerifier", "isUltimateUser: Valid tier block found: ${validTierBlock.tier}, valid from ${validTierBlock.validFrom} to ${validTierBlock.validUntil}")

            return validTierBlock.tier == "ULTIMATE"
        }
    }
