package nl.tudelft.trustchain.musicdao.core.ipv8.messages

import nl.tudelft.ipv8.messaging.*

/**
 * Message requesting a magnet link for a specific release
 */
class MagnetRequestMessage(
    val originPublicKey: ByteArray,
    var ttl: UInt,
    val releaseId: String
) : Serializable {
    override fun serialize(): ByteArray {
        return originPublicKey +
            serializeUInt(ttl) +
            serializeVarLen(releaseId.toByteArray(Charsets.US_ASCII))
    }

    fun checkTTL(): Boolean {
        ttl -= 1u
        if (ttl < 1u) return false
        return true
    }

    companion object Deserializer : Deserializable<MagnetRequestMessage> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<MagnetRequestMessage, Int> {
            var localOffset = 0
            val originPublicKey =
                buffer.copyOfRange(
                    offset + localOffset,
                    offset + localOffset + SERIALIZED_PUBLIC_KEY_SIZE
                )
            localOffset += SERIALIZED_PUBLIC_KEY_SIZE
            val ttl = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val (releaseId, releaseIdSize) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += releaseIdSize
            return Pair(
                MagnetRequestMessage(
                    originPublicKey,
                    ttl,
                    releaseId.toString(Charsets.US_ASCII)
                ),
                localOffset
            )
        }
    }
}
