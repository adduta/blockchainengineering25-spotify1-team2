package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UserTierBlockTest {
    @Test
    fun `test UserTierBlock creation with valid parameters`() {
        val userId = "test_user"
        val tier = "PRO"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + (30L * 24L * 60L * 60L * 1000L) // 30 days in milliseconds

        val block =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )

        assertEquals(userId, block.userId)
        assertEquals(tier, block.tier)
        assertEquals(validFrom, block.validFrom)
        assertEquals(validUntil, block.validUntil)
    }

    @Test
    fun `test UserTierBlock creation with null validUntil`() {
        val userId = "test_user"
        val tier = "ULTIMATE"
        val validFrom = System.currentTimeMillis()

        val block =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = null
            )

        assertEquals(userId, block.userId)
        assertEquals(tier, block.tier)
        assertEquals(validFrom, block.validFrom)
        assertNull(block.validUntil)
    }

    @Test
    fun `test UserTierBlock BLOCK_TYPE constant`() {
        assertEquals("user_tier", UserTierBlock.BLOCK_TYPE)
    }
}
