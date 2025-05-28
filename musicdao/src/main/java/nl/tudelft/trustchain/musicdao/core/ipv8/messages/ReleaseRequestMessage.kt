package nl.tudelft.trustchain.musicdao.core.ipv8.messages

data class ReleaseRequestMessage(
    val releaseId: String,
    val userPublicKey: String,
    val requestTimestamp: Long = System.currentTimeMillis()
)

data class ReleaseResponseMessage(
    val releaseId: String,
    val magnetLink: String?
)
