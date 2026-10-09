package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * The end of central directory record. APPNOTE.TXT §4.3.16.
 *
 * Fixed 22-byte record (plus an optional archive comment) that closes every
 * archive. It duplicates the central directory size and offset, each as a 32-bit
 * field; when the real value overflows, it saturates to `0xFFFFFFFF` and a Zip64
 * record (§4.3.14) carries the truth.
 */
data class EndOfCentralDirectoryRecord(
    val numberOfThisDisk: Int,
    val diskWithCentralDirectoryStart: Int,
    val entriesOnThisDisk: Int,
    val totalEntries: Int,
    val centralDirectorySize: Long,
    val centralDirectoryOffset: Long,
    val comment: String,
) {

    /** Serializes the record, saturating overflowing 16/32-bit fields. */
    fun serialize(): ByteArray {
        val commentBytes = comment.toByteArray(StandardCharsets.UTF_8)
        val entries = if (entriesOnThisDisk >= ZipFormat.UINT16_MAX) ZipFormat.UINT16_MAX else entriesOnThisDisk
        val total = if (totalEntries >= ZipFormat.UINT16_MAX) ZipFormat.UINT16_MAX else totalEntries
        val size = if (centralDirectorySize >= ZipFormat.UINT32_MAX) ZipFormat.UINT32_MAX else centralDirectorySize
        val offset =
            if (centralDirectoryOffset >= ZipFormat.UINT32_MAX) ZipFormat.UINT32_MAX else centralDirectoryOffset

        val out = ByteArrayOutputStream(ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE + commentBytes.size)
        LittleEndian.writeUInt32(out, ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE)
        LittleEndian.writeUInt16(out, numberOfThisDisk)
        LittleEndian.writeUInt16(out, diskWithCentralDirectoryStart)
        LittleEndian.writeUInt16(out, entries)
        LittleEndian.writeUInt16(out, total)
        LittleEndian.writeUInt32(out, size)
        LittleEndian.writeUInt32(out, offset)
        LittleEndian.writeUInt16(out, commentBytes.size)
        out.writeBytes(commentBytes)
        return out.toByteArray()
    }
}
