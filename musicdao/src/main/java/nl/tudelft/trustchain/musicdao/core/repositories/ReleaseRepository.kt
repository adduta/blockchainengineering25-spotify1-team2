package nl.tudelft.trustchain.musicdao.core.repositories

import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.MagnetResponseMessage
import javax.inject.Inject

class ReleaseRepository
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity
    ) {
        suspend fun getFullRelease(
            releaseId: String,
            userPublicKey: String
        ): String? {
            Log.d("ReleaseRepository", "Requesting magnet link for release $releaseId")

            // Try up to 3 times with increasing timeouts
            val timeouts = listOf(5000L, 10000L, 15000L)
            var response: MagnetResponseMessage? = null

            for ((attempt, timeout) in timeouts.withIndex()) {
                Log.d("ReleaseRepository", "Attempt ${attempt + 1} to get magnet link for release $releaseId with timeout ${timeout}ms")

                // Request magnet link from peers
                val peersCount = musicCommunity.requestMagnetLink(releaseId)
                Log.d("ReleaseRepository", "Sent magnet link request to $peersCount peers for release $releaseId")

                // Wait for response with timeout
                response = musicCommunity.getMagnetResponse(timeout)

                if (response != null && response.magnetLink.isNotEmpty()) {
                    Log.d("ReleaseRepository", "Received magnet link for release $releaseId on attempt ${attempt + 1}")
                    break
                } else {
                    Log.d("ReleaseRepository", "No magnet link response received for release $releaseId on attempt ${attempt + 1}")
                }
            }

            return response?.magnetLink
        }
    }
