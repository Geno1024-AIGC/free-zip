package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream

/**
 * The Zip64 extended information extra field. APPNOTE.TXT §4.5.3.
 *
 * Field order is fixed (original size, compressed size, local header offset,
 * disk start) and each value only appears when its 32-bit counterpart is
 * saturated.
 */
object Zip64ExtendedInformationExtraField {

    const val HEADER_ID = 0x0001

    /** Builds the field, or an empty array when no value is needed. */
    fun serialize(
        uncompressedSize: Long,
        compressedSize: Long,
        localHeaderOffset: Long,
        diskStart: Int,
        needUncompressed: Boolean,
        needCompressed: Boolean,
        needOffset: Boolean,
        needDisk: Boolean,
    ): ByteArray {
        if (!needUncompressed && !needCompressed && !needOffset && !needDisk) {
            return ByteArray(0)
        }
        val body = ByteArrayOutputStream(28)
        if (needUncompressed) LittleEndian.writeUInt64(body, uncompressedSize)
        if (needCompressed) LittleEndian.writeUInt64(body, compressedSize)
        if (needOffset) LittleEndian.writeUInt64(body, localHeaderOffset)
        if (needDisk) LittleEndian.writeUInt32(body, diskStart.toLong())

        val out = ByteArrayOutputStream(4 + body.size())
        LittleEndian.writeUInt16(out, HEADER_ID)
        LittleEndian.writeUInt16(out, body.size())
        out.writeBytes(body.toByteArray())
        return out.toByteArray()
    }
}
