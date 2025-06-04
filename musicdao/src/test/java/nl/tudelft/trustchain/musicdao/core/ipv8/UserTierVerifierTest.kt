package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("UserTierVerifier Tests")
class UserTierVerifierTest {
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var userTierVerifier: UserTierVerifier

    @BeforeEach
    fun setup() {
        userTierBlockRepository = mockk()
        userTierVerifier = UserTierVerifier(userTierBlockRepository)
    }

    @Test
    @DisplayName("Should return false when no tier blocks exist")
    fun `test isProUser with no tier blocks`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns emptyList()

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertFalse(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }

    @Test
    @DisplayName("Should return false when no valid tier blocks exist")
    fun `test isProUser with no valid tier blocks`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            val currentTime = System.currentTimeMillis()
            val expiredBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 1000
                    every { validUntil } returns currentTime - 500
                    every { tier } returns "PRO"
                }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(expiredBlock)

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertFalse(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }

    @Test
    @DisplayName("Should return true when valid PRO tier block exists")
    fun `test isProUser with valid PRO tier block`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            val currentTime = System.currentTimeMillis()
            val validProBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 1000
                    every { validUntil } returns currentTime + 1000
                    every { tier } returns "PRO"
                }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(validProBlock)

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertTrue(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }

    @Test
    @DisplayName("Should return false when valid BASIC tier block exists")
    fun `test isProUser with valid BASIC tier block`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            val currentTime = System.currentTimeMillis()
            val validBasicBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 1000
                    every { validUntil } returns currentTime + 1000
                    every { tier } returns "BASIC"
                }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(validBasicBlock)

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertFalse(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }

    @Test
    @DisplayName("Should return true when multiple valid blocks exist and most recent is PRO")
    fun `test isProUser with multiple valid blocks`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            val currentTime = System.currentTimeMillis()
            val olderProBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 2000
                    every { validUntil } returns currentTime + 1000
                    every { tier } returns "PRO"
                }
            val newerBasicBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 1000
                    every { validUntil } returns currentTime + 1000
                    every { tier } returns "BASIC"
                }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(olderProBlock, newerBasicBlock)

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertFalse(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }

    @Test
    @DisplayName("Should handle null validUntil as infinite validity")
    fun `test isProUser with null validUntil`() =
        runBlocking {
            // Arrange
            val userPublicKey = ByteArray(32) { 1 }
            val currentTime = System.currentTimeMillis()
            val infiniteProBlock =
                mockk<UserTierBlock> {
                    every { validFrom } returns currentTime - 1000
                    every { validUntil } returns null
                    every { tier } returns "PRO"
                }
            coEvery { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(infiniteProBlock)

            // Act
            val result = userTierVerifier.isProUser(userPublicKey)

            // Assert
            assertTrue(result)
            coVerify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
        }
}
