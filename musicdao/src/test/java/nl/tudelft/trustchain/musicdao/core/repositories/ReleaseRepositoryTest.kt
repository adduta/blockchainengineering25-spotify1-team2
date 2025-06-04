package nl.tudelft.trustchain.musicdao.core.repositories

import nl.tudelft.trustchain.musicdao.core.ipv8.ReleaseRequestHandler
import nl.tudelft.trustchain.musicdao.core.ipv8.messages.ReleaseResponseMessage
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("ReleaseRepository Tests")
class ReleaseRepositoryTest {
    private lateinit var releaseRequestHandler: ReleaseRequestHandler
    private lateinit var releaseRepository: ReleaseRepository

    @BeforeEach
    fun setup() {
        releaseRequestHandler = mockk()
        releaseRepository = ReleaseRepository(releaseRequestHandler)
    }

    @Test
    @DisplayName("Should return magnet link when request is successful")
    fun `test getFullRelease with successful request`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release-123"
            val userPublicKey = "test-key-456"
            val magnetLink = "magnet:?xt=urn:btih:test"
            coEvery {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            } returns
                ReleaseResponseMessage(
                    releaseId = releaseId,
                    magnetLink = magnetLink
                )

            // Act
            val result = releaseRepository.getFullRelease(releaseId, userPublicKey)

            // Assert
            assertEquals(magnetLink, result)
            coVerify {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            }
        }

    @Test
    @DisplayName("Should return null when request returns null magnet link")
    fun `test getFullRelease with null magnet link`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release-123"
            val userPublicKey = "test-key-456"
            coEvery {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            } returns
                ReleaseResponseMessage(
                    releaseId = releaseId,
                    magnetLink = null
                )

            // Act
            val result = releaseRepository.getFullRelease(releaseId, userPublicKey)

            // Assert
            assertNull(result)
            coVerify {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            }
        }

    @Test
    @DisplayName("Should handle request with different release ID")
    fun `test getFullRelease with different release ID`() =
        runBlocking {
            // Arrange
            val releaseId = "different-release-789"
            val userPublicKey = "test-key-456"
            val magnetLink = "magnet:?xt=urn:btih:different"
            coEvery {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            } returns
                ReleaseResponseMessage(
                    releaseId = releaseId,
                    magnetLink = magnetLink
                )

            // Act
            val result = releaseRepository.getFullRelease(releaseId, userPublicKey)

            // Assert
            assertEquals(magnetLink, result)
            coVerify {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            }
        }

    @Test
    @DisplayName("Should handle request with different user public key")
    fun `test getFullRelease with different user public key`() =
        runBlocking {
            // Arrange
            val releaseId = "test-release-123"
            val userPublicKey = "different-key-789"
            val magnetLink = "magnet:?xt=urn:btih:test"
            coEvery {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            } returns
                ReleaseResponseMessage(
                    releaseId = releaseId,
                    magnetLink = magnetLink
                )

            // Act
            val result = releaseRepository.getFullRelease(releaseId, userPublicKey)

            // Assert
            assertEquals(magnetLink, result)
            coVerify {
                releaseRequestHandler.handleRequest(
                    match {
                        it.releaseId == releaseId && it.userPublicKey == userPublicKey
                    }
                )
            }
        }
} 
