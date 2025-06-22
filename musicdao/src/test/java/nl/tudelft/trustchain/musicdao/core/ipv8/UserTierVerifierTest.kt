package nl.tudelft.trustchain.musicdao.core.ipv8

import io.mockk.*
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class UserTierVerifierTest {
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var verifier: UserTierVerifier
    private val userPublicKey = ByteArray(32) { 1 }

    @BeforeEach
    fun setup() {
        userTierBlockRepository = mockk()
        verifier = UserTierVerifier(userTierBlockRepository)
    }

    @Test
    fun `test isProUser returns false when no blocks exist`() {
        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns emptyList()

        val result = verifier.isProUser(userPublicKey)

        assertFalse(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isProUser returns true for PRO tier`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "PRO",
                validFrom = currentTime - 1000,
                validUntil = currentTime + 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isProUser(userPublicKey)

        assertTrue(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isProUser returns true for ULTIMATE tier`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = currentTime - 1000,
                validUntil = currentTime + 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isProUser(userPublicKey)

        assertTrue(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isProUser returns false for expired block`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "PRO",
                validFrom = currentTime - 2000,
                validUntil = currentTime - 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isProUser(userPublicKey)

        assertFalse(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isUltimateUser returns false when no blocks exist`() {
        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns emptyList()

        val result = verifier.isUltimateUser(userPublicKey)

        assertFalse(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isUltimateUser returns true for ULTIMATE tier`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = currentTime - 1000,
                validUntil = currentTime + 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isUltimateUser(userPublicKey)

        assertTrue(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isUltimateUser returns false for PRO tier`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "PRO",
                validFrom = currentTime - 1000,
                validUntil = currentTime + 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isUltimateUser(userPublicKey)

        assertFalse(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isUltimateUser returns false for expired block`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = currentTime - 2000,
                validUntil = currentTime - 1000
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isUltimateUser(userPublicKey)

        assertFalse(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }

    @Test
    fun `test isUltimateUser returns true for block with null validUntil`() {
        val currentTime = System.currentTimeMillis()
        val block =
            UserTierBlock(
                userId = "test_user",
                tier = "ULTIMATE",
                validFrom = currentTime - 1000,
                validUntil = null
            )

        every { userTierBlockRepository.getBlocksForUser(userPublicKey) } returns listOf(block)

        val result = verifier.isUltimateUser(userPublicKey)

        assertTrue(result)
        verify { userTierBlockRepository.getBlocksForUser(userPublicKey) }
    }
}
