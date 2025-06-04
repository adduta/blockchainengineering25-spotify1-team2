package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("UserTierBlockRepository Tests")
class UserTierBlockRepositoryTest {
    private lateinit var musicCommunity: MusicCommunity
    private lateinit var userTierBlockRepository: UserTierBlockRepository
    private lateinit var mockPublicKey: PublicKey
    private lateinit var mockPrivateKey: PrivateKey

    @BeforeEach
    fun setup() {
        musicCommunity = mockk()
        userTierBlockRepository = UserTierBlockRepository(musicCommunity)
        mockPublicKey = mockk()
        mockPrivateKey = mockk()
    }

    @Test
    @DisplayName("Should get blocks for user")
    fun `test getBlocksForUser`() {
        // Arrange
        val userPublicKey = ByteArray(32) { 1 }
        val trustChainBlock =
            mockk<TrustChainBlock> {
                every { publicKey } returns userPublicKey
                every { transaction } returns
                    mapOf(
                        "type" to UserTierBlock.BLOCK_TYPE,
                        "userId" to "testUser",
                        "tier" to "PRO",
                        "validFrom" to 1000L,
                        "validUntil" to 2000L
                    )
            }
        every { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) } returns listOf(trustChainBlock)

        // Act
        val blocks = userTierBlockRepository.getBlocksForUser(userPublicKey)

        // Assert
        assertEquals(1, blocks.size)
        assertEquals("testUser", blocks[0].userId)
        assertEquals("PRO", blocks[0].tier)
        assertEquals(1000L, blocks[0].validFrom)
        assertEquals(2000L, blocks[0].validUntil)
        verify { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) }
    }

    @Test
    @DisplayName("Should create block successfully")
    fun `test create block`() {
        // Arrange
        val userId = "testUser"
        val tier = "PRO"
        val validFrom = 1000L
        val validUntil = 2000L
        val expectedBlock = mockk<TrustChainBlock>()
        val expectedTransaction =
            mapOf(
                "type" to UserTierBlock.BLOCK_TYPE,
                "userId" to userId,
                "tier" to tier,
                "validFrom" to validFrom,
                "validUntil" to validUntil
            )
        every { musicCommunity.myPeer.publicKey.keyToBin() } returns ByteArray(32) { 1 }
        coEvery {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = expectedTransaction,
                publicKey = any()
            )
        } returns expectedBlock

        // Act
        val result = userTierBlockRepository.create(userId, tier, validFrom, validUntil)

        // Assert
        assertEquals(expectedBlock, result)
        coVerify {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = expectedTransaction,
                publicKey = any()
            )
        }
    }

    @Test
    @DisplayName("Should create block with null validUntil")
    fun `test create block with null validUntil`() {
        // Arrange
        val userId = "testUser"
        val tier = "BASIC"
        val validFrom = 1000L
        val expectedBlock = mockk<TrustChainBlock>()
        val expectedTransaction =
            mapOf(
                "type" to UserTierBlock.BLOCK_TYPE,
                "userId" to userId,
                "tier" to tier,
                "validFrom" to validFrom,
                "validUntil" to null
            )
        every { musicCommunity.myPeer.publicKey.keyToBin() } returns ByteArray(32) { 1 }
        coEvery {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = expectedTransaction,
                publicKey = any()
            )
        } returns expectedBlock

        // Act
        val result = userTierBlockRepository.create(userId, tier, validFrom, null)

        // Assert
        assertEquals(expectedBlock, result)
        coVerify {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = expectedTransaction,
                publicKey = any()
            )
        }
    }

    @Test
    @DisplayName("Should convert TrustChainBlock to UserTierBlock")
    fun `test toBlock conversion`() {
        // Arrange
        val trustChainBlock =
            mockk<TrustChainBlock> {
                every { transaction } returns
                    mapOf(
                        "type" to UserTierBlock.BLOCK_TYPE,
                        "userId" to "testUser",
                        "tier" to "PRO",
                        "validFrom" to 1000L,
                        "validUntil" to 2000L
                    )
            }

        // Act
        val result = userTierBlockRepository.toBlock(trustChainBlock)

        // Assert
        assertEquals("testUser", result.userId)
        assertEquals("PRO", result.tier)
        assertEquals(1000L, result.validFrom)
        assertEquals(2000L, result.validUntil)
    }

    @Test
    @DisplayName("Should convert TrustChainBlock to UserTierBlock with null validUntil")
    fun `test toBlock conversion with null validUntil`() {
        // Arrange
        val trustChainBlock =
            mockk<TrustChainBlock> {
                every { transaction } returns
                    mapOf(
                        "type" to UserTierBlock.BLOCK_TYPE,
                        "userId" to "testUser",
                        "tier" to "BASIC",
                        "validFrom" to 1000L,
                        "validUntil" to null
                    )
            }

        // Act
        val result = userTierBlockRepository.toBlock(trustChainBlock)

        // Assert
        assertEquals("testUser", result.userId)
        assertEquals("BASIC", result.tier)
        assertEquals(1000L, result.validFrom)
        assertNull(result.validUntil)
    }

    @Test
    @DisplayName("Should return empty list when no blocks found for user")
    fun `test getBlocksForUser with no blocks`() {
        // Arrange
        val userPublicKey = ByteArray(32) { 1 }
        every { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) } returns emptyList()

        // Act
        val blocks = userTierBlockRepository.getBlocksForUser(userPublicKey)

        // Assert
        assertTrue(blocks.isEmpty())
        verify { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) }
    }
}
