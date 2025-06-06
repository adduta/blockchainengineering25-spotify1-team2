package nl.tudelft.trustchain.musicdao.core.repositories

import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
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
            // Request magnet link from peers
            val peersCount = musicCommunity.requestMagnetLink(releaseId)
            Log.d("ReleaseRepository", "Sent magnet link request to $peersCount peers for release $releaseId")

            // Wait for response with timeout
            val response = musicCommunity.getMagnetResponse()
            if (response != null && response.magnetLink.isNotEmpty()) {
                Log.d("ReleaseRepository", "Received magnet link for release $releaseId")
            } else {
                Log.d("ReleaseRepository", "No magnet link response received for release $releaseId")
            }

            return response?.magnetLink
        }
    }
