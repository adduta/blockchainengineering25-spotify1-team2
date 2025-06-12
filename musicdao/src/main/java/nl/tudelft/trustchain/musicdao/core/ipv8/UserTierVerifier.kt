package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import javax.inject.Inject

class UserTierVerifier
    @Inject
    constructor(
        private val userTierBlockRepository: UserTierBlockRepository
    ) {
        suspend fun isProUser(userPublicKey: ByteArray): Boolean {
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

            return validTierBlock.tier == "PRO"
        }
    }
