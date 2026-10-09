package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream

/**
 * The Zip64 end of central directory locator. APPNOTE.TXT §4.3.15.
 *
 * Fixed 20-byte record placed immediately before the classic end of central
 * directory record; it points at the Zip64 record (§4.3.14) that precedes it.
 */
data class Zip64EndOfCentralDirectoryLocator(
    val diskWithZip64EndOfCentralDirectory: Long,
    val zip64EndOfCentralDirectoryOffset: Long,
    val totalNumberOfDisks: Long,
) {

    /** Serializes the fixed 20-byte record. */
    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream(ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE)
        LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE)
        LittleEndian.writeUInt32(out, diskWithZip64EndOfCentralDirectory)
        LittleEndian.writeUInt64(out, zip64EndOfCentralDirectoryOffset)
        LittleEndian.writeUInt32(out, totalNumberOfDisks)
        return out.toByteArray()
    }
}
