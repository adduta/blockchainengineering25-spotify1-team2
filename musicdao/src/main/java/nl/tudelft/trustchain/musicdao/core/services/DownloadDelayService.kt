package nl.tudelft.trustchain.musicdao.core.services

import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadDelayService @Inject constructor() {
    fun canDownloadNow(album: Album): Boolean {
        // TODO: add logic for basic users

        val releaseTime = album.releaseDate
        val currentTime = Instant.now()
        val hoursSinceRelease = ChronoUnit.HOURS.between(releaseTime, currentTime)

        return true
    }

    fun getRemainingDelay(album: Album): Long {

        val releaseTime = album.releaseDate
        val currentTime = Instant.now()
        val hoursSinceRelease = ChronoUnit.HOURS.between(releaseTime, currentTime)

        return if (hoursSinceRelease > 24) 24 - hoursSinceRelease else 0
    }
}
