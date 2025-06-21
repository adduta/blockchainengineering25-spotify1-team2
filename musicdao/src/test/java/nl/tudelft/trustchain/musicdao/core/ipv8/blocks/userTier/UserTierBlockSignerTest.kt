//package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier
//
//import io.mockk.*
//import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
//import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Assertions.*
//
//class UserTierBlockSignerTest {
//    private lateinit var musicCommunity: MusicCommunity
//    private lateinit var signer: UserTierBlockSigner
//
//    @BeforeEach
//    fun setup() {
//        musicCommunity = mockk()
//        signer = UserTierBlockSigner(musicCommunity)
//    }
//
//    @Test
//    fun `test onSignatureRequest creates agreement block`() {
//        val block = mockk<TrustChainBlock>()
//        val agreementBlock = mockk<TrustChainBlock>()
//
//        coEvery {
//            musicCommunity.createAgreementBlock(block, mapOf<Any?, Any?>())
//        } returns agreementBlock
//
//        signer.onSignatureRequest(block)
//
//        coVerify {
//            musicCommunity.createAgreementBlock(block, mapOf<Any?, Any?>())
//        }
//    }
//}
