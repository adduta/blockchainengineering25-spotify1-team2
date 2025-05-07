package nl.tudelft.trustchain.musicdao.core.util

import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import java.time.LocalDateTime
import java.util.Date

/**
 * DAOReputationSystem is responsible for calculating the reputation score of a user
 */
class DAOReputationSystem {
    /**
     * Calculate the reputation score of a user based on TrustChain history
     */
    fun calculateReputationScore(publicKey: ByteArray): Int {
        val trustChain = IPv8Android.getInstance().getOverlay<TrustChainCommunity>()

        // Factors that affect reputationScore
        // 1. Age of account
        // 2. Positive interactions
        // 3. Consistent voting patterns
        val blocks = trustChain?.database?.getMutualBlocks(publicKey, 100) ?: return 0
        val ageOfAccount = blocks.first().timestamp.time
        val currentDate = Date().time
        // Convert to minutes
        val accountAge = (currentDate - ageOfAccount) / (1000 * 60)

        val positiveInteractions = blocks.filter { block -> block.isAgreement }.size
        // TODO: Implement consistent voting patterns
        val consistentVotingPatterns = blocks.filter { block -> block.isProposal}.size

        // Calculate reputation score
        // TODO: more fine-grained calculation
        val reputationScore = (accountAge / 1000 * 60 * 60 * 24).toInt() + positiveInteractions + consistentVotingPatterns

        return reputationScore
    }

    /**
     * Check if a peer score meets the threshold
     */
    fun meetsReputationThreshold(publicKey: ByteArray, minimumScore: Int): Boolean {
        return calculateReputationScore(publicKey) >= minimumScore
    }

}
