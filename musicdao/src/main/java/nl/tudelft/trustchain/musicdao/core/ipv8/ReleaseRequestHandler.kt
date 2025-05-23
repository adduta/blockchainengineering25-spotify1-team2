package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseRequestMessage
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseResponseMessage
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.ipv8.util.hexToBytes
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ReleaseRequestHandler @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val userTierVerifier: UserTierVerifier
) {
    companion object {
        private const val DELAY_DAYS = 7L
    }

    suspend fun handleRequest(request: ReleaseRequestMessage): ReleaseResponseMessage {
        val release = albumRepository.getReleaseById(request.releaseId) ?: return ReleaseResponseMessage(
            releaseId = request.releaseId,
            magnetLink = null
        )

        val releaseDate = release.releaseDate
        val userPublicKeyBytes = request.userPublicKey.hexToBytes()
        val isProUser = userTierVerifier.isProUser(userPublicKeyBytes)
        val isAccessible = isProUser || isReleaseAccessible(releaseDate)

        return ReleaseResponseMessage(
            releaseId = request.releaseId,
            magnetLink = if (isAccessible) release.magnet else null
        )
    }

    private fun isReleaseAccessible(releaseDate: Instant): Boolean {
        val now = Instant.now()
        return ChronoUnit.DAYS.between(releaseDate, now) >= DELAY_DAYS
    }
}
