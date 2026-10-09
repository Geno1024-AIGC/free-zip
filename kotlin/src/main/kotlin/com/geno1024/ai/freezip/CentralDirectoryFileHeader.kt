package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * The central directory file header. APPNOTE.TXT §4.3.12.
 *
 * Collectively these headers form the central directory, the archive's index.
 * The only field that cannot be known when an entry is packed is the "relative
 * offset of local header", supplied when the archive is assembled. A local header
 * offset that no longer fits in 32 bits travels in the Zip64 extended information
 * extra field (§4.5.3).
 */
data class CentralDirectoryFileHeader(
    val versionMadeBy: Int,
    val versionNeeded: Int,
    val flags: Int,
    val method: CompressionMethod,
    val dosTime: MsDosDateTime,
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
) {

    /** Serializes the header, saturating size/offset fields for Zip64. */
    fun serialize(): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val commentBytes = comment.toByteArray(StandardCharsets.UTF_8)
        val needUncompressed = uncompressedSize >= ZipFormat.UINT32_MAX
        val needCompressed = compressedSize >= ZipFormat.UINT32_MAX
        val needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX

        val out = ByteArrayOutputStream(
            ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE + nameBytes.size + extra.size + commentBytes.size,
        )
        LittleEndian.writeUInt32(out, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE)
        LittleEndian.writeUInt16(out, versionMadeBy)
        LittleEndian.writeUInt16(out, versionNeeded)
        LittleEndian.writeUInt16(out, flags)
        LittleEndian.writeUInt16(out, method.id)
        LittleEndian.writeUInt16(out, dosTime.time)
        LittleEndian.writeUInt16(out, dosTime.date)
        LittleEndian.writeUInt32(out, crc32)
        LittleEndian.writeUInt32(out, if (needCompressed) ZipFormat.UINT32_MAX else compressedSize)
        LittleEndian.writeUInt32(out, if (needUncompressed) ZipFormat.UINT32_MAX else uncompressedSize)
        LittleEndian.writeUInt16(out, nameBytes.size)
        LittleEndian.writeUInt16(out, extra.size)
        LittleEndian.writeUInt16(out, commentBytes.size)
        LittleEndian.writeUInt16(out, diskNumberStart)
        LittleEndian.writeUInt16(out, internalAttributes)
        LittleEndian.writeUInt32(out, externalAttributes.toLong() and ZipFormat.UINT32_MAX)
        LittleEndian.writeUInt32(out, if (needOffset) ZipFormat.UINT32_MAX else localHeaderOffset)
        out.writeBytes(nameBytes)
        out.writeBytes(extra)
        out.writeBytes(commentBytes)
        return out.toByteArray()
    }

    companion object {
        /** Builds the header for [entry] located at [localHeaderOffset]. */
        fun of(entry: StoredEntry, localHeaderOffset: Long): CentralDirectoryFileHeader {
            val needUncompressed = entry.uncompressedSize >= ZipFormat.UINT32_MAX
            val needCompressed = entry.compressedSize >= ZipFormat.UINT32_MAX
            val needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX
            val extra = Zip64ExtendedInformationExtraField.serialize(
                entry.uncompressedSize, entry.compressedSize, localHeaderOffset, 0,
                needUncompressed, needCompressed, needOffset, false,
            )

            var versionNeeded = entry.method.versionNeeded
            if (needUncompressed || needCompressed || needOffset) {
                versionNeeded = maxOf(versionNeeded, ZipFormat.VERSION_NEEDED_ZIP64)
            }
            return CentralDirectoryFileHeader(
                entry.versionMadeBy, versionNeeded, entry.flags, entry.method, entry.dosTime,
                entry.crc32, entry.compressedSize, entry.uncompressedSize, 0, 0,
                entry.externalAttributes, localHeaderOffset, entry.name, extra, entry.comment ?: "",
            )
        }

        /** Parses a central directory file header, resolving Zip64 fields. */
        fun parse(bytes: ByteArray): CentralDirectoryFileHeader {
            requireSignature(bytes, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE)
            val versionMadeBy = LittleEndian.readUInt16(bytes, 4)
            val versionNeeded = LittleEndian.readUInt16(bytes, 6)
            val flags = LittleEndian.readUInt16(bytes, 8)
            val method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 10))
            val dosTime = MsDosDateTime(LittleEndian.readUInt16(bytes, 12), LittleEndian.readUInt16(bytes, 14))
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

            ExtraField.find(extra, Zip64ExtendedInformationExtraField.HEADER_ID)?.let { zip64 ->
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

        private fun requireSignature(bytes: ByteArray, expected: Long) {
            require(bytes.size >= 4 && LittleEndian.readUInt32(bytes, 0) == expected) {
                "not a central directory file header (unexpected signature)"
            }
        }

        private fun charsetFor(flags: Int): Charset =
            if (flags and GeneralPurposeBitFlag.UTF8 != 0) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
    }
}
