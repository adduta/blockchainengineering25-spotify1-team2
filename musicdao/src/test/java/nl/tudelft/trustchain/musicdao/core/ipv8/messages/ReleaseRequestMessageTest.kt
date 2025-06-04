package nl.tudelft.trustchain.musicdao.core.ipv8.messages

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

@DisplayName("ReleaseRequestMessage Tests")
class ReleaseRequestMessageTest {
    @Test
    @DisplayName("Should create ReleaseRequestMessage with all properties")
    fun `test ReleaseRequestMessage creation with all properties`() {
        // Arrange
        val releaseId = "test-release-123"
        val userPublicKey = "test-key-456"
        val requestTimestamp = System.currentTimeMillis()

        // Act
        val message =
            ReleaseRequestMessage(
                releaseId = releaseId,
                userPublicKey = userPublicKey,
                requestTimestamp = requestTimestamp
            )

        // Assert
        assertEquals(releaseId, message.releaseId)
        assertEquals(userPublicKey, message.userPublicKey)
        assertEquals(requestTimestamp, message.requestTimestamp)
    }

    @Test
    @DisplayName("Should create ReleaseRequestMessage with default timestamp")
    fun `test ReleaseRequestMessage creation with default timestamp`() {
        // Arrange
        val releaseId = "test-release-123"
        val userPublicKey = "test-key-456"

        // Act
        val message =
            ReleaseRequestMessage(
                releaseId = releaseId,
                userPublicKey = userPublicKey
            )

        // Assert
        assertEquals(releaseId, message.releaseId)
        assertEquals(userPublicKey, message.userPublicKey)
        assertTrue(message.requestTimestamp > 0)
    }

    @Test
    @DisplayName("Should create equal messages with same properties")
    fun `test ReleaseRequestMessage equality`() {
        // Arrange
        val releaseId = "test-release-123"
        val userPublicKey = "test-key-456"
        val requestTimestamp = System.currentTimeMillis()

        // Act
        val message1 =
            ReleaseRequestMessage(
                releaseId = releaseId,
                userPublicKey = userPublicKey,
                requestTimestamp = requestTimestamp
            )
        val message2 =
            ReleaseRequestMessage(
                releaseId = releaseId,
                userPublicKey = userPublicKey,
                requestTimestamp = requestTimestamp
            )

        // Assert
        assertEquals(message1, message2)
        assertEquals(message1.hashCode(), message2.hashCode())
    }

    @Test
    @DisplayName("Should create different messages with different properties")
    fun `test ReleaseRequestMessage inequality`() {
        // Arrange
        val releaseId = "test-release-123"
        val userPublicKey = "test-key-456"
        val requestTimestamp = System.currentTimeMillis()

        // Act
        val message1 =
            ReleaseRequestMessage(
                releaseId = releaseId,
                userPublicKey = userPublicKey,
                requestTimestamp = requestTimestamp
            )
        val message2 =
            ReleaseRequestMessage(
                releaseId = "different-release",
                userPublicKey = userPublicKey,
                requestTimestamp = requestTimestamp
            )

        // Assert
        assertNotEquals(message1, message2)
        assertNotEquals(message1.hashCode(), message2.hashCode())
    }
}

@DisplayName("ReleaseResponseMessage Tests")
class ReleaseResponseMessageTest {
    @Test
    @DisplayName("Should create ReleaseResponseMessage with magnet link")
    fun `test ReleaseResponseMessage creation with magnet link`() {
        // Arrange
        val releaseId = "test-release-123"
        val magnetLink = "magnet:?xt=urn:btih:test"

        // Act
        val message =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = magnetLink
            )

        // Assert
        assertEquals(releaseId, message.releaseId)
        assertEquals(magnetLink, message.magnetLink)
    }

    @Test
    @DisplayName("Should create ReleaseResponseMessage with null magnet link")
    fun `test ReleaseResponseMessage creation with null magnet link`() {
        // Arrange
        val releaseId = "test-release-123"

        // Act
        val message =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = null
            )

        // Assert
        assertEquals(releaseId, message.releaseId)
        assertNull(message.magnetLink)
    }

    @Test
    @DisplayName("Should create equal messages with same properties")
    fun `test ReleaseResponseMessage equality`() {
        // Arrange
        val releaseId = "test-release-123"
        val magnetLink = "magnet:?xt=urn:btih:test"

        // Act
        val message1 =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = magnetLink
            )
        val message2 =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = magnetLink
            )

        // Assert
        assertEquals(message1, message2)
        assertEquals(message1.hashCode(), message2.hashCode())
    }

    @Test
    @DisplayName("Should create different messages with different properties")
    fun `test ReleaseResponseMessage inequality`() {
        // Arrange
        val releaseId = "test-release-123"
        val magnetLink = "magnet:?xt=urn:btih:test"

        // Act
        val message1 =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = magnetLink
            )
        val message2 =
            ReleaseResponseMessage(
                releaseId = "different-release",
                magnetLink = magnetLink
            )

        // Assert
        assertNotEquals(message1, message2)
        assertNotEquals(message1.hashCode(), message2.hashCode())
    }

    @Test
    @DisplayName("Should create different messages with different magnet links")
    fun `test ReleaseResponseMessage inequality with different magnet links`() {
        // Arrange
        val releaseId = "test-release-123"

        // Act
        val message1 =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = "magnet:?xt=urn:btih:test1"
            )
        val message2 =
            ReleaseResponseMessage(
                releaseId = releaseId,
                magnetLink = "magnet:?xt=urn:btih:test2"
            )

        // Assert
        assertNotEquals(message1, message2)
        assertNotEquals(message1.hashCode(), message2.hashCode())
    }
} 
