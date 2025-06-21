package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.userTier.UserTierBlockValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.Peer
import javax.inject.Inject

class UserTierBlockGossiper
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
        private val userTierBlockValidator: UserTierBlockValidator
    ) {
        fun startGossip(coroutineScope: CoroutineScope) {
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    gossip()
                    delay(Config.DELAY)
                }
            }
        }

        private fun gossip() {
            val randomPeer = pickRandomPeer()
            val userTierBlocks =
                musicCommunity.database.getBlocksWithType(UserTierBlock.BLOCK_TYPE)
                    .filter { userTierBlockValidator.validate(it, musicCommunity.database) is nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult.Valid }
                    .shuffled()
                    .take(Config.BLOCKS)
            userTierBlocks.forEach {
                musicCommunity.sendBlock(it, randomPeer)
            }
        }

        object Config {
            const val BLOCKS = 5
            const val DELAY = 15_000L
        }

        private fun pickRandomPeer(): Peer? {
            val peers = musicCommunity.getPeers()
            if (peers.isEmpty()) return null
            return peers.random()
        }
    }
