package com.geno1024.ai.freezip

/**
 * A parsed local file header. APPNOTE.TXT §4.3.7.
 *
 * Zip64 size fields have already been resolved, so [compressedSize] and
 * [uncompressedSize] are the effective values.
 */
data class LocalFileHeader(
    val versionNeeded: Int,
    val flags: Int,
    val method: CompressionMethod,
    val dosTime: DosTime,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val name: String,
    val extra: ByteArray,
)
