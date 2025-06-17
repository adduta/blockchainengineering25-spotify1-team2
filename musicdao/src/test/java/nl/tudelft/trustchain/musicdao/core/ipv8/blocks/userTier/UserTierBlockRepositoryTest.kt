package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier

import io.mockk.*
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class UserTierBlockRepositoryTest {
    private lateinit var musicCommunity: MusicCommunity
    private lateinit var repository: UserTierBlockRepository

    @BeforeEach
    fun setup() {
        musicCommunity = mockk()
        every { musicCommunity.myPeer } returns mockk(relaxed = true)
        repository = UserTierBlockRepository(musicCommunity)
        val publicKey = "test_public_key".toByteArray()
        val mockPeer = mockk<Peer>()
        val mockPublicKey = mockk<PublicKey>()
        every { musicCommunity.myPeer } returns mockPeer
        every { mockPeer.publicKey } returns mockPublicKey
        every { mockPublicKey.keyToBin() } returns publicKey
    }

    @Test
    fun `test getBlocksForUser returns empty list when no blocks exist`() {
        val userPublicKey = ByteArray(32) { 1 }
        coEvery { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) } returns emptyList()

        val result = repository.getBlocksForUser(userPublicKey)

        assertTrue(result.isEmpty())
        coVerify { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) }
    }

    @Test
    fun `test getBlocksForUser filters blocks by public key`() {
        val userPublicKey = ByteArray(32) { 1 }
        val otherPublicKey = ByteArray(32) { 2 }

        val block1 = mockk<TrustChainBlock>()
        val block2 = mockk<TrustChainBlock>()

        every { block1.publicKey } returns userPublicKey
        every { block2.publicKey } returns otherPublicKey

        every { block1.transaction } returns mapOf(
            "type" to UserTierBlock.BLOCK_TYPE,
            "userId" to "user1",
            "tier" to "PRO",
            "validFrom" to 1000L,
            "validUntil" to 2000L
        )

        coEvery { musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE) } returns listOf(block1, block2)

        val result = repository.getBlocksForUser(userPublicKey)

        assertEquals(1, result.size)
        assertEquals("user1", result[0].userId)
        assertEquals("PRO", result[0].tier)
        assertEquals(1000L, result[0].validFrom)
        assertEquals(2000L, result[0].validUntil)
    }

    @Test
    fun `test create returns null when block creation fails`() {
        val userId = "test_user"
        val tier = "PRO"
        val validFrom = System.currentTimeMillis()
        val validUntil = validFrom + (30L * 24L * 60L * 60L * 1000L)
        val publicKey = "test_public_key".toByteArray()

        every { musicCommunity.getPeers() } returns mockk(relaxed = true)
        coEvery {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = mapOf(
                    "type" to UserTierBlock.BLOCK_TYPE,
                    "userId" to userId,
                    "tier" to tier,
                    "validFrom" to validFrom,
                    "validUntil" to validUntil
                ),
                publicKey
            )
        } throws RuntimeException()

        val result = repository.create(userId, tier, validFrom, validUntil)
        assertNull(result)

        coVerify {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = mapOf(
                    "type" to UserTierBlock.BLOCK_TYPE,
                    "userId" to userId,
                    "tier" to tier,
                    "validFrom" to validFrom,
                    "validUntil" to validUntil
                ),
                publicKey
            )
        }
    }

    @Test
    fun `test create returns block when creation succeeds`() {
        val userId = "test_user"
        val tier = "ULTIMATE"
        val validFrom = System.currentTimeMillis()
        val validUntil = null
        val publicKey = "test_public_key".toByteArray()

        val expectedBlock = mockk<TrustChainBlock>()
        coEvery {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = mapOf(
                    "type" to UserTierBlock.BLOCK_TYPE,
                    "userId" to userId,
                    "tier" to tier,
                    "validFrom" to validFrom,
                    "validUntil" to validUntil
                ),
                publicKey
            )
        } returns expectedBlock

        val result = repository.create(userId, tier, validFrom, validUntil)

        assertEquals(expectedBlock, result)
        coVerify {
            musicCommunity.createProposalBlock(
                blockType = UserTierBlock.BLOCK_TYPE,
                transaction = mapOf(
                    "type" to UserTierBlock.BLOCK_TYPE,
                    "userId" to userId,
                    "tier" to tier,
                    "validFrom" to validFrom,
                    "validUntil" to validUntil
                ),
                publicKey
            )
        }
    }

    @Test
    fun `test toBlock converts TrustChainBlock to UserTierBlock`() {
        val block = mockk<TrustChainBlock>()
        val transaction = mapOf(
            "type" to UserTierBlock.BLOCK_TYPE,
            "userId" to "test_user",
            "tier" to "PRO",
            "validFrom" to 1000L,
            "validUntil" to 2000L
        )

        every { block.transaction } returns transaction

        val result = repository.toBlock(block)

        assertEquals("test_user", result.userId)
        assertEquals("PRO", result.tier)
        assertEquals(1000L, result.validFrom)
        assertEquals(2000L, result.validUntil)
    }
}
