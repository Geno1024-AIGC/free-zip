package com.geno1024.ai.freezip

/**
 * A parsed central directory file header. APPNOTE.TXT §4.3.12.
 *
 * Zip64 size and offset fields have already been resolved.
 */
data class CentralDirectoryFileHeader(
    val versionMadeBy: Int,
    val versionNeeded: Int,
    val flags: Int,
    val method: CompressionMethod,
    val dosTime: DosTime,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val diskNumberStart: Int,
    val internalAttributes: Int,
    val externalAttributes: Int,
    val localHeaderOffset: Long,
    val name: String,
    val extra: ByteArray,
    val comment: String,
)
