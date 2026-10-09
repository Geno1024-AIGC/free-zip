package com.geno1024.ai.freezip

import java.io.ByteArrayOutputStream

/**
 * Serializes [StoredEntry] metadata into the two ZIP headers.
 *
 * Each entry's metadata is written twice, consistently: once as a
 * `local file header` (APPNOTE.TXT §4.3.7) that precedes the payload, and once
 * as a `central directory file header` (§4.3.12) collected at the end of the
 * archive. The only field unknown when the local header is written is the
 * "relative offset of local header", supplied to [centralDirectoryFileHeader].
 */
object HeaderCodec {

    /** Builds a complete local file header. APPNOTE.TXT §4.3.7. */
    fun localFileHeader(entry: StoredEntry): ByteArray {
        val name = entry.nameBytes
        val zip64 = entry.needsZip64Sizes
        // §4.5.3: the local Zip64 extra must carry BOTH original and compressed size.
        val extra = if (zip64) {
            zip64Extra(entry.uncompressedSize, entry.compressedSize, 0, 0, true, true, false, false)
        } else {
            ByteArray(0)
        }

        val out = ByteArrayOutputStream(ZipFormat.LOCAL_FILE_HEADER_FIXED_SIZE + name.size + extra.size)
        LittleEndian.writeUInt32(out, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE)
        LittleEndian.writeUInt16(out, entry.versionNeeded)
        LittleEndian.writeUInt16(out, entry.flags)
        LittleEndian.writeUInt16(out, entry.method.id)
        LittleEndian.writeUInt16(out, entry.dosTime.time)
        LittleEndian.writeUInt16(out, entry.dosTime.date)
        LittleEndian.writeUInt32(out, entry.crc32)
        LittleEndian.writeUInt32(out, if (zip64) ZipFormat.UINT32_MAX else entry.compressedSize)
        LittleEndian.writeUInt32(out, if (zip64) ZipFormat.UINT32_MAX else entry.uncompressedSize)
        LittleEndian.writeUInt16(out, name.size)
        LittleEndian.writeUInt16(out, extra.size)
        out.writeBytes(name)
        out.writeBytes(extra)
        return out.toByteArray()
    }

    /**
     * Builds a central directory file header. APPNOTE.TXT §4.3.12.
     *
     * @param localHeaderOffset offset of the matching local file header; emitted
     *   through the Zip64 extra field when it no longer fits in 32 bits
     */
    fun centralDirectoryFileHeader(entry: StoredEntry, localHeaderOffset: Long): ByteArray {
        val name = entry.nameBytes
        val comment = entry.commentBytes

        val needUncompressed = entry.uncompressedSize >= ZipFormat.UINT32_MAX
        val needCompressed = entry.compressedSize >= ZipFormat.UINT32_MAX
        val needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX
        val extra = zip64Extra(
            entry.uncompressedSize, entry.compressedSize, localHeaderOffset, 0,
            needUncompressed, needCompressed, needOffset, false,
        )

        var versionNeeded = entry.method.versionNeeded
        if (needUncompressed || needCompressed || needOffset) {
            versionNeeded = maxOf(versionNeeded, ZipFormat.VERSION_NEEDED_ZIP64)
        }

        val out = ByteArrayOutputStream(
            ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE + name.size + extra.size + comment.size,
        )
        LittleEndian.writeUInt32(out, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE)
        LittleEndian.writeUInt16(out, entry.versionMadeBy)
        LittleEndian.writeUInt16(out, versionNeeded)
        LittleEndian.writeUInt16(out, entry.flags)
        LittleEndian.writeUInt16(out, entry.method.id)
        LittleEndian.writeUInt16(out, entry.dosTime.time)
        LittleEndian.writeUInt16(out, entry.dosTime.date)
        LittleEndian.writeUInt32(out, entry.crc32)
        LittleEndian.writeUInt32(out, if (needCompressed) ZipFormat.UINT32_MAX else entry.compressedSize)
        LittleEndian.writeUInt32(out, if (needUncompressed) ZipFormat.UINT32_MAX else entry.uncompressedSize)
        LittleEndian.writeUInt16(out, name.size)
        LittleEndian.writeUInt16(out, extra.size)
        LittleEndian.writeUInt16(out, comment.size)
        LittleEndian.writeUInt16(out, 0)                       // disk number start
        LittleEndian.writeUInt16(out, 0)                       // internal file attributes
        LittleEndian.writeUInt32(out, entry.externalAttributes.toLong() and ZipFormat.UINT32_MAX)
        LittleEndian.writeUInt32(out, if (needOffset) ZipFormat.UINT32_MAX else localHeaderOffset)
        out.writeBytes(name)
        out.writeBytes(extra)
        out.writeBytes(comment)
        return out.toByteArray()
    }

    /**
     * Builds a Zip64 extended information extra field. APPNOTE.TXT §4.5.3.
     *
     * The field order is fixed (original size, compressed size, local header
     * offset, disk start) and each value only appears when its 32-bit counterpart
     * is saturated.
     */
    fun zip64Extra(
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
        LittleEndian.writeUInt16(out, ZipFormat.EXTRA_FIELD_ZIP64)
        LittleEndian.writeUInt16(out, body.size())
        out.writeBytes(body.toByteArray())
        return out.toByteArray()
    }
}
