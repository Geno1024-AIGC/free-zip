package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;

/**
 * Serializes {@link StoredEntry} metadata into the two ZIP headers.
 *
 * <p>Each entry's metadata is written twice, byte-for-byte consistently:
 * once as a {@code local file header} (APPNOTE.TXT §4.3.7) that precedes the
 * payload, and once as a {@code central directory file header} (§4.3.12) that
 * is collected at the end of the archive. The only field that cannot be known
 * when the local header is written is the "relative offset of local header",
 * which is supplied to {@link #centralDirectoryFileHeader}.
 */
public final class HeaderCodec {

    private HeaderCodec() {
    }

    /**
     * Builds a complete local file header. APPNOTE.TXT §4.3.7.
     *
     * <p>The general purpose bit 3 (data descriptor) is never set: all sizes and
     * the CRC-32 are known up front, so no trailing descriptor is needed.
     */
    public static byte[] localFileHeader(StoredEntry entry) {
        byte[] name = entry.nameBytes();
        boolean zip64 = entry.needsZip64Sizes();
        // §4.5.3: the local Zip64 extra must carry BOTH original and compressed size.
        byte[] extra = zip64
                ? zip64Extra(entry.uncompressedSize(), entry.compressedSize(), 0, 0, true, true, false, false)
                : new byte[0];

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.LOCAL_FILE_HEADER_FIXED_SIZE + name.length + extra.length);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE);
            LittleEndian.writeUInt16(out, entry.versionNeeded());
            LittleEndian.writeUInt16(out, entry.flags());
            LittleEndian.writeUInt16(out, entry.method().id());
            LittleEndian.writeUInt16(out, entry.dosTime().time());
            LittleEndian.writeUInt16(out, entry.dosTime().date());
            LittleEndian.writeUInt32(out, entry.crc32());
            LittleEndian.writeUInt32(out, zip64 ? ZipFormat.UINT32_MAX : entry.compressedSize());
            LittleEndian.writeUInt32(out, zip64 ? ZipFormat.UINT32_MAX : entry.uncompressedSize());
            LittleEndian.writeUInt16(out, name.length);
            LittleEndian.writeUInt16(out, extra.length);
            out.writeBytes(name);
            out.writeBytes(extra);
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }

    /**
     * Builds a central directory file header. APPNOTE.TXT §4.3.12.
     *
     * @param localHeaderOffset offset of the matching local file header from the
     *                          start of the archive; emitted through the Zip64
     *                          extra field when it no longer fits in 32 bits
     */
    public static byte[] centralDirectoryFileHeader(StoredEntry entry, long localHeaderOffset) {
        byte[] name = entry.nameBytes();
        byte[] comment = entry.commentBytes();

        boolean needUncompressed = entry.uncompressedSize() >= ZipFormat.UINT32_MAX;
        boolean needCompressed = entry.compressedSize() >= ZipFormat.UINT32_MAX;
        boolean needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX;
        byte[] extra = zip64Extra(entry.uncompressedSize(), entry.compressedSize(),
                localHeaderOffset, 0, needUncompressed, needCompressed, needOffset, false);

        int versionNeeded = entry.method().versionNeeded();
        if (needUncompressed || needCompressed || needOffset) {
            versionNeeded = Math.max(versionNeeded, ZipFormat.VERSION_NEEDED_ZIP64);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE + name.length + extra.length + comment.length);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
            LittleEndian.writeUInt16(out, entry.versionMadeBy());
            LittleEndian.writeUInt16(out, versionNeeded);
            LittleEndian.writeUInt16(out, entry.flags());
            LittleEndian.writeUInt16(out, entry.method().id());
            LittleEndian.writeUInt16(out, entry.dosTime().time());
            LittleEndian.writeUInt16(out, entry.dosTime().date());
            LittleEndian.writeUInt32(out, entry.crc32());
            LittleEndian.writeUInt32(out, needCompressed ? ZipFormat.UINT32_MAX : entry.compressedSize());
            LittleEndian.writeUInt32(out, needUncompressed ? ZipFormat.UINT32_MAX : entry.uncompressedSize());
            LittleEndian.writeUInt16(out, name.length);
            LittleEndian.writeUInt16(out, extra.length);
            LittleEndian.writeUInt16(out, comment.length);
            LittleEndian.writeUInt16(out, 0);                 // disk number start
            LittleEndian.writeUInt16(out, 0);                 // internal file attributes
            LittleEndian.writeUInt32(out, entry.externalAttributes());
            LittleEndian.writeUInt32(out, needOffset ? ZipFormat.UINT32_MAX : localHeaderOffset);
            out.writeBytes(name);
            out.writeBytes(extra);
            out.writeBytes(comment);
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }

    /**
     * Builds a Zip64 extended information extra field. APPNOTE.TXT §4.5.3.
     *
     * <p>The field order is fixed (original size, compressed size, local header
     * offset, disk start) and each value only appears when its 32-bit counterpart
     * is saturated.
     */
    public static byte[] zip64Extra(long uncompressedSize, long compressedSize, long localHeaderOffset,
                                    int diskStart, boolean needUncompressed, boolean needCompressed,
                                    boolean needOffset, boolean needDisk) {
        if (!needUncompressed && !needCompressed && !needOffset && !needDisk) {
            return new byte[0];
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(28);
        try {
            if (needUncompressed) {
                LittleEndian.writeUInt64(body, uncompressedSize);
            }
            if (needCompressed) {
                LittleEndian.writeUInt64(body, compressedSize);
            }
            if (needOffset) {
                LittleEndian.writeUInt64(body, localHeaderOffset);
            }
            if (needDisk) {
                LittleEndian.writeUInt32(body, diskStart);
            }
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + body.size());
        try {
            LittleEndian.writeUInt16(out, ZipFormat.EXTRA_FIELD_ZIP64);
            LittleEndian.writeUInt16(out, body.size());
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }
}
