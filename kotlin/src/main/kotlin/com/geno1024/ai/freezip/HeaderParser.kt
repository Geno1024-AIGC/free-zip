package com.geno1024.ai.freezip

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Parses ZIP headers back into metadata.
 *
 * Inverse of [HeaderCodec]: reads a `local file header` (APPNOTE.TXT §4.3.7) and
 * a `central directory file header` (§4.3.12), resolving any Zip64 extended
 * information extra field (§4.5.3). Used by the assembler to rebuild the central
 * directory with correct local header offsets.
 */
object HeaderParser {

    /** Parses a local file header and resolves Zip64 sizes. */
    fun parseLocalFileHeader(bytes: ByteArray): LocalFileHeader {
        requireSignature(bytes, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE)
        val versionNeeded = LittleEndian.readUInt16(bytes, 4)
        val flags = LittleEndian.readUInt16(bytes, 6)
        val method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 8))
        val dosTime = DosTime(LittleEndian.readUInt16(bytes, 10), LittleEndian.readUInt16(bytes, 12))
        val crc32 = LittleEndian.readUInt32(bytes, 14)
        var compressedSize = LittleEndian.readUInt32(bytes, 18)
        var uncompressedSize = LittleEndian.readUInt32(bytes, 22)
        val nameLength = LittleEndian.readUInt16(bytes, 26)
        val extraLength = LittleEndian.readUInt16(bytes, 28)
        val name = String(bytes, 30, nameLength, charsetFor(flags))
        val extra = bytes.copyOfRange(30 + nameLength, 30 + nameLength + extraLength)

        findExtra(extra, ZipFormat.EXTRA_FIELD_ZIP64)?.let { zip64 ->
            var cursor = 0
            if (uncompressedSize == ZipFormat.UINT32_MAX) {
                uncompressedSize = LittleEndian.readUInt64(zip64, cursor)
                cursor += 8
            }
            if (compressedSize == ZipFormat.UINT32_MAX) {
                compressedSize = LittleEndian.readUInt64(zip64, cursor)
            }
        }
        return LocalFileHeader(
            versionNeeded, flags, method, dosTime, crc32,
            compressedSize, uncompressedSize, name, extra,
        )
    }

    /** Parses a central directory file header and resolves Zip64 fields. */
    fun parseCentralDirectoryFileHeader(bytes: ByteArray): CentralDirectoryFileHeader {
        requireSignature(bytes, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE)
        val versionMadeBy = LittleEndian.readUInt16(bytes, 4)
        val versionNeeded = LittleEndian.readUInt16(bytes, 6)
        val flags = LittleEndian.readUInt16(bytes, 8)
        val method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 10))
        val dosTime = DosTime(LittleEndian.readUInt16(bytes, 12), LittleEndian.readUInt16(bytes, 14))
        val crc32 = LittleEndian.readUInt32(bytes, 16)
        var compressedSize = LittleEndian.readUInt32(bytes, 20)
        var uncompressedSize = LittleEndian.readUInt32(bytes, 24)
        val nameLength = LittleEndian.readUInt16(bytes, 28)
        val extraLength = LittleEndian.readUInt16(bytes, 30)
        val commentLength = LittleEndian.readUInt16(bytes, 32)
        var diskNumberStart = LittleEndian.readUInt16(bytes, 34)
        val internalAttributes = LittleEndian.readUInt16(bytes, 36)
        val externalAttributes = LittleEndian.readUInt32(bytes, 38).toInt()
        var localHeaderOffset = LittleEndian.readUInt32(bytes, 42)

        var cursor = ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE
        val name = String(bytes, cursor, nameLength, charsetFor(flags))
        cursor += nameLength
        val extra = bytes.copyOfRange(cursor, cursor + extraLength)
        cursor += extraLength
        val comment = String(bytes, cursor, commentLength, charsetFor(flags))

        findExtra(extra, ZipFormat.EXTRA_FIELD_ZIP64)?.let { zip64 ->
            var p = 0
            if (uncompressedSize == ZipFormat.UINT32_MAX) {
                uncompressedSize = LittleEndian.readUInt64(zip64, p)
                p += 8
            }
            if (compressedSize == ZipFormat.UINT32_MAX) {
                compressedSize = LittleEndian.readUInt64(zip64, p)
                p += 8
            }
            if (localHeaderOffset == ZipFormat.UINT32_MAX) {
                localHeaderOffset = LittleEndian.readUInt64(zip64, p)
                p += 8
            }
            if (diskNumberStart == ZipFormat.UINT16_MAX) {
                diskNumberStart = LittleEndian.readUInt32(zip64, p).toInt()
            }
        }

        return CentralDirectoryFileHeader(
            versionMadeBy, versionNeeded, flags, method, dosTime, crc32,
            compressedSize, uncompressedSize, diskNumberStart, internalAttributes,
            externalAttributes, localHeaderOffset, name, extra, comment,
        )
    }

    /**
     * Combines the two parsed headers into the metadata needed to rebuild the
     * central directory, validating that the duplicated fields agree.
     */
    fun toStoredEntry(local: LocalFileHeader, central: CentralDirectoryFileHeader): StoredEntry {
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

    /** Finds an extra field by header id, walking the §4.5.2 TLV list. */
    fun findExtra(extra: ByteArray, id: Int): ByteArray? {
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

    private fun requireSignature(bytes: ByteArray, expected: Long) {
        require(bytes.size >= 4 && LittleEndian.readUInt32(bytes, 0) == expected) {
            "not a ZIP header (unexpected signature)"
        }
    }

    private fun charsetFor(flags: Int): Charset =
        if (flags and ZipFormat.FLAG_UTF8 != 0) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
}
