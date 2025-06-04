package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("UserTierBlock Tests")
class UserTierBlockTest {
    @Test
    @DisplayName("Should create UserTierBlock with all properties")
    fun `test UserTierBlock creation with all properties`() {
        // Arrange
        val userId = "testUser123"
        val tier = "PRO"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + 1000L

        // Act
        val block =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )

        // Assert
        assertEquals(userId, block.userId)
        assertEquals(tier, block.tier)
        assertEquals(validFrom, block.validFrom)
        assertEquals(validUntil, block.validUntil)
    }

    @Test
    @DisplayName("Should create UserTierBlock with null validUntil")
    fun `test UserTierBlock creation with null validUntil`() {
        // Arrange
        val userId = "testUser123"
        val tier = "BASIC"
        val validFrom = System.currentTimeMillis()

        // Act
        val block =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = null
            )

        // Assert
        assertEquals(userId, block.userId)
        assertEquals(tier, block.tier)
        assertEquals(validFrom, block.validFrom)
        assertNull(block.validUntil)
    }

    @Test
    @DisplayName("Should have correct BLOCK_TYPE constant")
    fun `test UserTierBlock BLOCK_TYPE constant`() {
        // Assert
        assertEquals("user_tier", UserTierBlock.BLOCK_TYPE)
    }

    @Test
    @DisplayName("Should create equal blocks with same properties")
    fun `test UserTierBlock equality`() {
        // Arrange
        val userId = "testUser123"
        val tier = "PRO"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + 1000L

        // Act
        val block1 =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )
        val block2 =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )

        // Assert
        assertEquals(block1, block2)
        assertEquals(block1.hashCode(), block2.hashCode())
    }

    @Test
    @DisplayName("Should create different blocks with different properties")
    fun `test UserTierBlock inequality`() {
        // Arrange
        val userId = "testUser123"
        val tier = "PRO"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + 1000L

        // Act
        val block1 =
            UserTierBlock(
                userId = userId,
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )
        val block2 =
            UserTierBlock(
                userId = "differentUser",
                tier = tier,
                validFrom = validFrom,
                validUntil = validUntil
            )

        // Assert
        assertNotEquals(block1, block2)
        assertNotEquals(block1.hashCode(), block2.hashCode())
    }

    @Test
    @DisplayName("Should create different blocks with different tiers")
    fun `test UserTierBlock inequality with different tiers`() {
        // Arrange
        val userId = "testUser123"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + 1000L

        // Act
        val block1 =
            UserTierBlock(
                userId = userId,
                tier = "PRO",
                validFrom = validFrom,
                validUntil = validUntil
            )
        val block2 =
            UserTierBlock(
                userId = userId,
                tier = "BASIC",
                validFrom = validFrom,
                validUntil = validUntil
            )

        // Assert
        assertNotEquals(block1, block2)
        assertNotEquals(block1.hashCode(), block2.hashCode())
    }
} 
