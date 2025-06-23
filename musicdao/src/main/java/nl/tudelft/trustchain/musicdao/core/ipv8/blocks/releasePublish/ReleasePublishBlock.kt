package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish

data class ReleasePublishBlock(
    val releaseId: String,
    val title: String,
    val artist: String,
    val publisher: String,
    val releaseDate: String,
    val protocolVersion: String,
    val magnet: String? = null,
    val isExclusive: Boolean = false
) {
    companion object {
        const val BLOCK_TYPE = "publish_release"
    }
}
