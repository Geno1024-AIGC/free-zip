package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream

/**
 * The Zip64 end of central directory record. APPNOTE.TXT §4.3.14.
 *
 * Present only when Zip64 is in use; it repeats the end-of-central-directory
 * totals as 64-bit fields. The size field (§4.3.14.1) counts everything after
 * itself, i.e. the fixed record length minus the signature and the size field.
 */
data class Zip64EndOfCentralDirectoryRecord(
    val versionMadeBy: Int,
    val versionNeeded: Int,
    val numberOfThisDisk: Long,
    val diskWithCentralDirectoryStart: Long,
    val entriesOnThisDisk: Long,
    val totalEntries: Long,
    val centralDirectorySize: Long,
    val centralDirectoryOffset: Long,
) {

    /** Serializes the fixed 56-byte record. */
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream(ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE)
        LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE)
        // §4.3.14.1: size excludes the leading 12 bytes.
        LittleEndian.writeUInt64(out, (ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE - 12).toLong())
        LittleEndian.writeUInt16(out, versionMadeBy)
        LittleEndian.writeUInt16(out, versionNeeded)
        LittleEndian.writeUInt32(out, numberOfThisDisk)
        LittleEndian.writeUInt32(out, diskWithCentralDirectoryStart)
        LittleEndian.writeUInt64(out, entriesOnThisDisk)
        LittleEndian.writeUInt64(out, totalEntries)
        LittleEndian.writeUInt64(out, centralDirectorySize)
        LittleEndian.writeUInt64(out, centralDirectoryOffset)
        return out.toByteArray()
    }
}
