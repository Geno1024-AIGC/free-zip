package com.geno1024.ai.freezip

/**
 * Helpers for walking the extra field area of a header.
 *
 * APPNOTE.TXT §4.5.2: the area is a sequence of `[header id:2][data size:2][data]`
 * triplets until the declared area length is consumed.
 */
object ExtraField {

    /** Returns the payload of the first field with [id], or {@code null}. */
    fun find(extra: ByteArray, id: Int): ByteArray? {
        var cursor = 0
        while (cursor + 4 <= extra.size) {
            val headerId = LittleEndian.readUInt16(extra, cursor)
            val size = LittleEndian.readUInt16(extra, cursor + 2)
            cursor += 4
            if (cursor + size > extra.size) {
                return null
            }
            if (headerId == id) {
                return extra.copyOfRange(cursor, cursor + size)
            }
            cursor += size
        }
        return null
    }
}
