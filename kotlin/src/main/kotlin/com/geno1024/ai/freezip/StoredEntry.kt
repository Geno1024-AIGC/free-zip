package com.geno1024.ai.freezip

import java.nio.charset.StandardCharsets

/**
 * Immutable metadata describing one stored entry.
 *
 * This is the in-memory shape of a `.lfh` / `.cdfh` pair: every value needed to
 * emit both headers. The compressed payload itself lives in the matching `.data`
 * file and is referenced by [compressedSize].
 *
 * Field semantics follow PKWARE APPNOTE.TXT: [name] is stored in the header
 * (§4.3.7 / §4.3.12), [hostSystem] and [externalAttributes] populate
 * "version made by" and "external file attributes" (§4.4.2, §4.4.15), and
 * [dosTime] follows §4.4.6.
 */
data class StoredEntry(
    val name: String,
    val method: CompressionMethod,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val dosTime: MsDosDateTime,
    val hostSystem: Int,
    val externalAttributes: Int,
    val comment: String?,
) {
    init {
        require(name.isNotEmpty()) { "entry name must not be empty" }
        require(crc32 in 0L..ZipFormat.UINT32_MAX) { "crc32 out of range: $crc32" }
        require(compressedSize >= 0 && uncompressedSize >= 0) { "sizes must not be negative" }
    }

    /** General purpose bit flag. APPNOTE.TXT §4.4.4 (bit 11 = UTF-8 name). */
    val flags: Int
        get() = if (isAscii(name) && isAscii(comment)) 0 else GeneralPurposeBitFlag.UTF8

    /** True when either 32-bit size field would overflow and Zip64 is required. */
    val needsZip64Sizes: Boolean
        get() = uncompressedSize >= ZipFormat.UINT32_MAX || compressedSize >= ZipFormat.UINT32_MAX

    /** Minimum "version needed to extract". APPNOTE.TXT §4.4.3. */
    val versionNeeded: Int
        get() {
            var version = method.versionNeeded
            if (needsZip64Sizes) {
                version = maxOf(version, ZipFormat.VERSION_NEEDED_ZIP64)
            }
            return version
        }

    /** "Version made by": host system byte plus the spec version. §4.4.2. */
    val versionMadeBy: Int
        get() = ((hostSystem and 0xFF) shl 8) or versionNeeded

    val nameBytes: ByteArray
        get() = name.toByteArray(StandardCharsets.UTF_8)

    val commentBytes: ByteArray
        get() = comment?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)

    companion object {
        /**
         * Combines the two parsed headers, validating that the duplicated fields
         * agree. Inverse of [LocalFileHeader.of] / [CentralDirectoryFileHeader.of].
         */
        fun of(local: LocalFileHeader, central: CentralDirectoryFileHeader): StoredEntry {
            require(local.name == central.name) {
                "name mismatch between local and central header: ${local.name} vs ${central.name}"
            }
            require(
                local.crc32 == central.crc32 &&
                    local.compressedSize == central.compressedSize &&
                    local.uncompressedSize == central.uncompressedSize &&
                    local.method == central.method,
            ) { "inconsistent size/crc/method for entry: ${local.name}" }

            return StoredEntry(
                name = local.name,
                method = local.method,
                crc32 = local.crc32,
                compressedSize = local.compressedSize,
                uncompressedSize = local.uncompressedSize,
                dosTime = local.dosTime,
                hostSystem = (central.versionMadeBy ushr 8) and 0xFF,
                externalAttributes = central.externalAttributes,
                comment = central.comment,
            )
        }

        private fun isAscii(value: String?): Boolean = value == null || value.all { it.code <= 0x7F }
    }
}
