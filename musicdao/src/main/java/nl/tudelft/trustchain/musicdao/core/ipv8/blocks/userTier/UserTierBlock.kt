package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

data class UserTierBlock(
    val userId: String,
    val tier: String,
    val validFrom: Long,
    val validUntil: Long?
) {
    companion object {
        const val BLOCK_TYPE = "user_tier"
    }
}
