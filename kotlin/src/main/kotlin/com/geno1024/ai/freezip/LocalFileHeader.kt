package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * The local file header. APPNOTE.TXT §4.3.7.
 *
 * One of these immediately precedes each entry's file data (§4.3.8). The record
 * holds the effective sizes: when either exceeds 32 bits the serialized 32-bit
 * fields saturate to `0xFFFFFFFF` and the true values travel in the Zip64
 * extended information extra field (§4.5.3).
 *
 * The general purpose bit 3 (data descriptor) is never set, so no trailing
 * descriptor is needed.
 */
data class LocalFileHeader(
    val versionNeeded: Int,
    val flags: Int,
    val method: CompressionMethod,
    val dosTime: MsDosDateTime,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val name: String,
    val extra: ByteArray,
) {

    /** True when either 32-bit size field saturates. */
    val needsZip64: Boolean
        get() = compressedSize >= ZipFormat.UINT32_MAX || uncompressedSize >= ZipFormat.UINT32_MAX

    /** Serializes the header, saturating the 32-bit size fields for Zip64. */
    fun serialize(): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val zip64 = needsZip64

        val out = ByteArrayOutputStream(
            ZipFormat.LOCAL_FILE_HEADER_FIXED_SIZE + nameBytes.size + extra.size,
        )
        LittleEndian.writeUInt32(out, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE)
        LittleEndian.writeUInt16(out, versionNeeded)
        LittleEndian.writeUInt16(out, flags)
        LittleEndian.writeUInt16(out, method.id)
        LittleEndian.writeUInt16(out, dosTime.time)
        LittleEndian.writeUInt16(out, dosTime.date)
        LittleEndian.writeUInt32(out, crc32)
        LittleEndian.writeUInt32(out, if (zip64) ZipFormat.UINT32_MAX else compressedSize)
        LittleEndian.writeUInt32(out, if (zip64) ZipFormat.UINT32_MAX else uncompressedSize)
        LittleEndian.writeUInt16(out, nameBytes.size)
        LittleEndian.writeUInt16(out, extra.size)
        out.writeBytes(nameBytes)
        out.writeBytes(extra)
        return out.toByteArray()
    }

    companion object {
        /** Builds the header for [entry]. */
        fun of(entry: StoredEntry): LocalFileHeader {
            val zip64 = entry.needsZip64Sizes
            // §4.5.3: the local Zip64 extra must carry BOTH original and compressed size.
            val extra = if (zip64) {
                Zip64ExtendedInformationExtraField.serialize(
                    entry.uncompressedSize, entry.compressedSize, 0, 0, true, true, false, false,
                )
            } else {
                ByteArray(0)
            }
            return LocalFileHeader(
                entry.versionNeeded, entry.flags, entry.method, entry.dosTime, entry.crc32,
                entry.compressedSize, entry.uncompressedSize, entry.name, extra,
            )
        }

        /** Parses a local file header, resolving Zip64 sizes. */
        fun parse(bytes: ByteArray): LocalFileHeader {
            requireSignature(bytes, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE)
            val versionNeeded = LittleEndian.readUInt16(bytes, 4)
            val flags = LittleEndian.readUInt16(bytes, 6)
            val method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 8))
            val dosTime = MsDosDateTime(LittleEndian.readUInt16(bytes, 10), LittleEndian.readUInt16(bytes, 12))
            val crc32 = LittleEndian.readUInt32(bytes, 14)
            var compressedSize = LittleEndian.readUInt32(bytes, 18)
            var uncompressedSize = LittleEndian.readUInt32(bytes, 22)
            val nameLength = LittleEndian.readUInt16(bytes, 26)
            val extraLength = LittleEndian.readUInt16(bytes, 28)
            val name = String(bytes, 30, nameLength, charsetFor(flags))
            val extra = bytes.copyOfRange(30 + nameLength, 30 + nameLength + extraLength)

            ExtraField.find(extra, Zip64ExtendedInformationExtraField.HEADER_ID)?.let { zip64 ->
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

        private fun requireSignature(bytes: ByteArray, expected: Long) {
            require(bytes.size >= 4 && LittleEndian.readUInt32(bytes, 0) == expected) {
                "not a local file header (unexpected signature)"
            }
        }

        private fun charsetFor(flags: Int): Charset =
            if (flags and GeneralPurposeBitFlag.UTF8 != 0) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
    }
}
