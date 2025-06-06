package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseRequestMessage
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseResponseMessage
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.ipv8.util.hexToBytes
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import android.util.Log
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MessageId

class ReleaseRequestHandler
    @Inject
    constructor(
        private val albumRepository: AlbumRepository,
        private val userTierVerifier: UserTierVerifier,
        private val musicCommunity: MusicCommunity
    ) {
        companion object {
            private const val DELAY_DAYS = 7L
            private const val PEER_RESPONSE_TIMEOUT = 5000L // 5 seconds timeout for peer responses
        }

        suspend fun handleRequest(request: ReleaseRequestMessage): ReleaseResponseMessage {
            val release =
                albumRepository.getReleaseById(request.releaseId) ?: return ReleaseResponseMessage(
                    releaseId = request.releaseId,
                    magnetLink = null
                )

            val releaseDate = release.releaseDate
            val userPublicKeyBytes = request.userPublicKey.hexToBytes()
            val isProUser = userTierVerifier.isProUser(userPublicKeyBytes)
            val isAccessible = isProUser || isReleaseAccessible(releaseDate)

            Log.d("ReleaseRequestHandler", "User isPro: $isProUser, isAccessible: $isAccessible")

            if (!isAccessible) {
                return ReleaseResponseMessage(
                    releaseId = request.releaseId,
                    magnetLink = null
                )
            }

            // If we have the magnet link locally, return it
            if (release.magnet.isNotEmpty()) {
                return ReleaseResponseMessage(
                    releaseId = request.releaseId,
                    magnetLink = release.magnet
                )
            }

            // If we don't have the magnet link locally, try to get it from peers
            val peers = musicCommunity.getPeers()
            for (peer in peers) {
                try {
                    val packet = musicCommunity.serializePacket(
                        MessageId.RELEASE_REQUEST_MESSAGE,
                        request
                    )
                    musicCommunity.send(peer, packet)
                    
                    // Wait for response from peer with timeout
                    val response = waitForPeerResponse(request.releaseId)
                    if (response != null) {
                        // If we got a valid response, store the magnet link locally
                        if (response.magnetLink != null) {
                            albumRepository.updateReleaseMagnet(request.releaseId, response.magnetLink)
                        }
                        return response
                    }
                } catch (e: Exception) {
                    Log.e("ReleaseRequestHandler", "Error getting magnet link from peer: ${e.message}")
                }
            }

            return ReleaseResponseMessage(
                releaseId = request.releaseId,
                magnetLink = null
            )
        }

        private fun isReleaseAccessible(releaseDate: Instant): Boolean {
            val now = Instant.now()
            return ChronoUnit.DAYS.between(releaseDate, now) >= DELAY_DAYS
        }

        private suspend fun waitForPeerResponse(releaseId: String): ReleaseResponseMessage? {
            return musicCommunity.getReleaseResponse(PEER_RESPONSE_TIMEOUT)
        }
    }
