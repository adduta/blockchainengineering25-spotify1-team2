package nl.tudelft.trustchain.musicdao.core.ipv8.messages

import nl.tudelft.ipv8.messaging.*

/**
 * Message containing a magnet link response for a specific release
 */
class MagnetResponseMessage(
    val releaseId: String,
    val magnetLink: String
) : Serializable {
    override fun serialize(): ByteArray {
        return serializeVarLen(releaseId.toByteArray(Charsets.US_ASCII)) +
            serializeVarLen(magnetLink.toByteArray(Charsets.US_ASCII))
    }

    companion object Deserializer : Deserializable<MagnetResponseMessage> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<MagnetResponseMessage, Int> {
            var localOffset = 0
            val (releaseId, releaseIdSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += releaseIdSize
            val (magnetLink, magnetLinkSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += magnetLinkSize
            return Pair(
                MagnetResponseMessage(
                    releaseId.toString(Charsets.US_ASCII),
                    magnetLink.toString(Charsets.US_ASCII)
                ),
                localOffset
            )
        }
    }
}
