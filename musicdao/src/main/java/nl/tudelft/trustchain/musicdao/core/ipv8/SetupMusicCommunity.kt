package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockSigner
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockValidator
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockSigner
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockValidator
import javax.inject.Inject

class SetupMusicCommunity
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
        private val releasePublishBlockSigner: ReleasePublishBlockSigner,
        private val releasePublishBlockValidator: ReleasePublishBlockValidator,
        private val userTierBlockSigner: UserTierBlockSigner,
        private val userTierBlockValidator: UserTierBlockValidator
    ) {
        fun registerListeners() {
            musicCommunity.registerTransactionValidator(
                ReleasePublishBlockValidator.BLOCK_TYPE,
                releasePublishBlockValidator
            )
            musicCommunity.registerBlockSigner(
                ReleasePublishBlockSigner.BLOCK_TYPE,
                releasePublishBlockSigner
            )

            // Register user tier block validator and signer
            musicCommunity.registerTransactionValidator(
                "user_tier",
                userTierBlockValidator
            )
            musicCommunity.registerBlockSigner(
                "user_tier",
                userTierBlockSigner
            )
        }
    }
