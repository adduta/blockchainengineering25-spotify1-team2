package nl.tudelft.trustchain.musicdao.core.ipv8

import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseRequestMessage
import nl.tudelft.trustchain.musicdao.core.repositories.AlbumRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Album
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import java.time.Instant

@DisplayName("ReleaseRequestHandler Tests")
class ReleaseRequestHandlerTest {
    private lateinit var albumRepository: AlbumRepository
    private lateinit var userTierVerifier: UserTierVerifier
    private lateinit var releaseRequestHandler: ReleaseRequestHandler

    @BeforeEach
    fun setup() {
        albumRepository = mockk()
        userTierVerifier = mockk()
        releaseRequestHandler = ReleaseRequestHandler(albumRepository, userTierVerifier)
    }

    @Test
    @DisplayName("Should return null magnet link when release not found")
    fun `test handleRequest with non-existent release`() =
        runBlocking {
            // Arrange
            val request =
                ReleaseRequestMessage(
                    releaseId = "non-existent-id",
                    userPublicKey = "test-key"
                )
            coEvery { albumRepository.getReleaseById(request.releaseId) } returns null

            // Act
            val response = releaseRequestHandler.handleRequest(request)

            // Assert
            assertEquals(request.releaseId, response.releaseId)
            assertNull(response.magnetLink)
            coVerify { albumRepository.getReleaseById(request.releaseId) }
        }

    @Test
    @DisplayName("Should return magnet link for PRO user regardless of release date")
    fun `test handleRequest for PRO user`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release"
            val magnetLink = "magnet:?xt=urn:btih:test"
            val request =
                ReleaseRequestMessage(
                    releaseId = releaseId,
                    userPublicKey = "test-key"
                )
            val album =
                mockk<Album> {
                    every { this@mockk.id } returns releaseId
                    every { this@mockk.magnet } returns magnetLink
                    every { this@mockk.releaseDate } returns Instant.now()
                }
            coEvery { albumRepository.getReleaseById(releaseId) } returns album
            coEvery { userTierVerifier.isProUser(any()) } returns true

            // Act
            val response = releaseRequestHandler.handleRequest(request)

            // Assert
            assertEquals(releaseId, response.releaseId)
            assertEquals(magnetLink, response.magnetLink)
            coVerify {
                albumRepository.getReleaseById(releaseId)
                userTierVerifier.isProUser(any())
            }
        }

    @Test
    @DisplayName("Should return magnet link for BASIC user when release is old enough")
    fun `test handleRequest for BASIC user with old release`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release"
            val magnetLink = "magnet:?xt=urn:btih:test"
            val request =
                ReleaseRequestMessage(
                    releaseId = releaseId,
                    userPublicKey = "test-key"
                )
            val oldReleaseDate = Instant.now().minus(8, java.time.temporal.ChronoUnit.DAYS)
            val album =
                mockk<Album> {
                    every { this@mockk.id } returns releaseId
                    every { this@mockk.magnet } returns magnetLink
                    every { this@mockk.releaseDate } returns oldReleaseDate
                }
            coEvery { albumRepository.getReleaseById(releaseId) } returns album
            coEvery { userTierVerifier.isProUser(any()) } returns false

            // Act
            val response = releaseRequestHandler.handleRequest(request)

            // Assert
            assertEquals(releaseId, response.releaseId)
            assertEquals(magnetLink, response.magnetLink)
            coVerify {
                albumRepository.getReleaseById(releaseId)
                userTierVerifier.isProUser(any())
            }
        }

    @Test
    @DisplayName("Should return null magnet link for BASIC user when release is too new")
    fun `test handleRequest for BASIC user with new release`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release"
            val magnetLink = "magnet:?xt=urn:btih:test"
            val request =
                ReleaseRequestMessage(
                    releaseId = releaseId,
                    userPublicKey = "test-key"
                )
            val newReleaseDate = Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS)
            val album =
                mockk<Album> {
                    every { this@mockk.id } returns releaseId
                    every { this@mockk.magnet } returns magnetLink
                    every { this@mockk.releaseDate } returns newReleaseDate
                }
            coEvery { albumRepository.getReleaseById(releaseId) } returns album
            coEvery { userTierVerifier.isProUser(any()) } returns false

            // Act
            val response = releaseRequestHandler.handleRequest(request)

            // Assert
            assertEquals(releaseId, response.releaseId)
            assertNull(response.magnetLink)
            coVerify {
                albumRepository.getReleaseById(releaseId)
                userTierVerifier.isProUser(any())
            }
        }

    @Test
    @DisplayName("Should handle exact 7-day old release correctly")
    fun `test handleRequest with exactly 7 days old release`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release"
            val magnetLink = "magnet:?xt=urn:btih:test"
            val request =
                ReleaseRequestMessage(
                    releaseId = releaseId,
                    userPublicKey = "test-key"
                )
            val sevenDaysOldRelease = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)
            val album =
                mockk<Album> {
                    every { this@mockk.id } returns releaseId
                    every { this@mockk.magnet } returns magnetLink
                    every { this@mockk.releaseDate } returns sevenDaysOldRelease
                }
            coEvery { albumRepository.getReleaseById(releaseId) } returns album
            coEvery { userTierVerifier.isProUser(any()) } returns false

            // Act
            val response = releaseRequestHandler.handleRequest(request)

            // Assert
            assertEquals(releaseId, response.releaseId)
            assertEquals(magnetLink, response.magnetLink)
            coVerify {
                albumRepository.getReleaseById(releaseId)
                userTierVerifier.isProUser(any())
            }
        }
}
