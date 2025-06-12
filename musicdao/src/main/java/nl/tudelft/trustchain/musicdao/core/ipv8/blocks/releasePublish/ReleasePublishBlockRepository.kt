package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish

import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.Constants
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.toHex
import javax.inject.Inject

class ReleasePublishBlockRepository
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
        private val releasePublishBlockValidator: ReleasePublishBlockValidator
    ) {
        fun getValidBlocks(): List<ReleasePublishBlock> {
            val blocks =
                musicCommunity.database.getBlocksWithType(ReleasePublishBlock.BLOCK_TYPE)
                    .filter { releasePublishBlockValidator.validateTransaction(it.transaction) }
            return blocks.map { toBlock(it) }
        }

        fun create(
            releaseId: String,
            magnet: String,
            title: String,
            artist: String,
            releaseDate: String,
            isExclusive: Boolean = false
        ): TrustChainBlock? {
            val myPeer = IPv8Android.getInstance().myPeer
            val transaction =
                mutableMapOf(
                    "releaseId" to releaseId,
                    "magnet" to "access_restricted",
                    "title" to title,
                    "artist" to artist,
                    "publisher" to myPeer.publicKey.keyToBin().toHex(),
                    "releaseDate" to releaseDate,
                    "protocolVersion" to Constants.PROTOCOL_VERSION,
                    "isExclusive" to isExclusive
                )

            if (!releasePublishBlockValidator.validateTransaction(transaction)) {
                Log.d("ReleasePublishBlockRepository", "Invalid transaction data")
                return null
            }

            Log.d(
                "ReleasePublishBlockRepository",
                "Creating ReleasePublishBlock with releaseId: $releaseId, title: $title, " +
                    "artist: $artist, publisher: ${myPeer.publicKey.keyToBin().toHex()}," +
                    " releaseDate: $releaseDate, isExclusive: $isExclusive"
            )

            val result = musicCommunity.createProposalBlock(
                ReleasePublishBlock.BLOCK_TYPE,
                transaction,
                myPeer.publicKey.keyToBin()
            )

            Log.d("ReleasePublishBlockRepository", "Created block: $result")
            return result
        }

        fun toBlock(block: TrustChainBlock): ReleasePublishBlock {
            val releaseId = block.transaction["releaseId"] as String
            val title = block.transaction["title"] as String
            val artist = block.transaction["artist"] as String
            val publisher = block.transaction["publisher"] as String
            val releaseDate = block.transaction["releaseDate"] as String
            val protocolVersion = block.transaction["protocolVersion"] as String
            // Handle old format blocks that might have a magnet link
            val magnet = block.transaction["magnet"] as? String
            // Handle old format blocks that don't have isExclusive field
            val isExclusive = block.transaction["isExclusive"] as? Boolean ?: false

            return ReleasePublishBlock(
                releaseId = releaseId,
                title = title,
                artist = artist,
                publisher = publisher,
                releaseDate = releaseDate,
                protocolVersion = protocolVersion,
                magnet = magnet,
                isExclusive = isExclusive
            )
        }
    }
