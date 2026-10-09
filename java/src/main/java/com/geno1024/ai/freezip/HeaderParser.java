package com.geno1024.ai.freezip;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Parses ZIP headers back into metadata.
 *
 * <p>Inverse of {@link HeaderCodec}: reads a {@code local file header}
 * (APPNOTE.TXT §4.3.7) and a {@code central directory file header} (§4.3.12),
 * resolving any Zip64 extended information extra field (§4.5.3). Used by the
 * assembler to rebuild the central directory with the correct local header
 * offsets.
 */
public final class HeaderParser {

    private HeaderParser() {
    }

    /** Parses a local file header and resolves Zip64 sizes. */
    public static LocalFileHeader parseLocalFileHeader(byte[] bytes) {
        requireSignature(bytes, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE);
        int versionNeeded = LittleEndian.readUInt16(bytes, 4);
        int flags = LittleEndian.readUInt16(bytes, 6);
        CompressionMethod method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 8));
        DosTime dosTime = new DosTime(LittleEndian.readUInt16(bytes, 10), LittleEndian.readUInt16(bytes, 12));
        long crc32 = LittleEndian.readUInt32(bytes, 14);
        long compressedSize = LittleEndian.readUInt32(bytes, 18);
        long uncompressedSize = LittleEndian.readUInt32(bytes, 22);
        int nameLength = LittleEndian.readUInt16(bytes, 26);
        int extraLength = LittleEndian.readUInt16(bytes, 28);
        String name = new String(bytes, 30, nameLength, charsetFor(flags));
        byte[] extra = slice(bytes, 30 + nameLength, extraLength);

        byte[] zip64 = findExtra(extra, ZipFormat.EXTRA_FIELD_ZIP64);
        if (zip64 != null) {
            int p = 0;
            if (uncompressedSize == ZipFormat.UINT32_MAX) {
                uncompressedSize = LittleEndian.readUInt64(zip64, p);
                p += 8;
            }
            if (compressedSize == ZipFormat.UINT32_MAX) {
                compressedSize = LittleEndian.readUInt64(zip64, p);
            }
        }
        return new LocalFileHeader(versionNeeded, flags, method, dosTime, crc32,
                compressedSize, uncompressedSize, name, extra);
    }

    /** Parses a central directory file header and resolves Zip64 fields. */
    public static CentralDirectoryFileHeader parseCentralDirectoryFileHeader(byte[] bytes) {
        requireSignature(bytes, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
        int versionMadeBy = LittleEndian.readUInt16(bytes, 4);
        int versionNeeded = LittleEndian.readUInt16(bytes, 6);
        int flags = LittleEndian.readUInt16(bytes, 8);
        CompressionMethod method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 10));
        DosTime dosTime = new DosTime(LittleEndian.readUInt16(bytes, 12), LittleEndian.readUInt16(bytes, 14));
        long crc32 = LittleEndian.readUInt32(bytes, 16);
        long compressedSize = LittleEndian.readUInt32(bytes, 20);
        long uncompressedSize = LittleEndian.readUInt32(bytes, 24);
        int nameLength = LittleEndian.readUInt16(bytes, 28);
        int extraLength = LittleEndian.readUInt16(bytes, 30);
        int commentLength = LittleEndian.readUInt16(bytes, 32);
        int diskNumberStart = LittleEndian.readUInt16(bytes, 34);
        int internalAttributes = LittleEndian.readUInt16(bytes, 36);
        int externalAttributes = (int) LittleEndian.readUInt32(bytes, 38);
        long localHeaderOffset = LittleEndian.readUInt32(bytes, 42);
        int cursor = ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE;
        String name = new String(bytes, cursor, nameLength, charsetFor(flags));
        cursor += nameLength;
        byte[] extra = slice(bytes, cursor, extraLength);
        cursor += extraLength;
        String comment = new String(bytes, cursor, commentLength, charsetFor(flags));

        byte[] zip64 = findExtra(extra, ZipFormat.EXTRA_FIELD_ZIP64);
        if (zip64 != null) {
            int p = 0;
            if (uncompressedSize == ZipFormat.UINT32_MAX) {
                uncompressedSize = LittleEndian.readUInt64(zip64, p);
                p += 8;
            }
            if (compressedSize == ZipFormat.UINT32_MAX) {
                compressedSize = LittleEndian.readUInt64(zip64, p);
                p += 8;
            }
            if (localHeaderOffset == ZipFormat.UINT32_MAX) {
                localHeaderOffset = LittleEndian.readUInt64(zip64, p);
                p += 8;
            }
            if (diskNumberStart == ZipFormat.UINT16_MAX) {
                diskNumberStart = (int) LittleEndian.readUInt32(zip64, p);
            }
        }
        return new CentralDirectoryFileHeader(versionMadeBy, versionNeeded, flags, method, dosTime,
                crc32, compressedSize, uncompressedSize, diskNumberStart, internalAttributes,
                externalAttributes, localHeaderOffset, name, extra, comment);
    }

    /**
     * Combines the two parsed headers into the metadata needed to rebuild the
     * central directory, validating that the duplicated fields agree.
     */
    public static StoredEntry toStoredEntry(LocalFileHeader local, CentralDirectoryFileHeader central) {
        if (!local.name().equals(central.name())) {
            throw new IllegalArgumentException(
                    "name mismatch between local and central header: " + local.name() + " vs " + central.name());
        }
        if (local.crc32() != central.crc32()
                || local.compressedSize() != central.compressedSize()
                || local.uncompressedSize() != central.uncompressedSize()
                || local.method() != central.method()) {
            throw new IllegalArgumentException("inconsistent size/crc/method for entry: " + local.name());
        }
        return new StoredEntry(
                local.name(),
                local.method(),
                local.crc32(),
                local.compressedSize(),
                local.uncompressedSize(),
                local.dosTime(),
                (central.versionMadeBy() >>> 8) & 0xFF,
                central.externalAttributes(),
                central.comment());
    }

    private static void requireSignature(byte[] bytes, long expected) {
        if (bytes.length < 4 || LittleEndian.readUInt32(bytes, 0) != expected) {
            throw new IllegalArgumentException("not a ZIP header (unexpected signature)");
        }
    }

    private static Charset charsetFor(int flags) {
        return (flags & ZipFormat.FLAG_UTF8) != 0
                ? StandardCharsets.UTF_8
                : StandardCharsets.ISO_8859_1;
    }

    private static byte[] slice(byte[] bytes, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(bytes, offset, out, 0, length);
        return out;
    }

    /** Finds an extra field by header id, walking the §4.5.2 TLV list. */
    public static byte[] findExtra(byte[] extra, int id) {
        int cursor = 0;
        while (cursor + 4 <= extra.length) {
            int headerId = LittleEndian.readUInt16(extra, cursor);
            int size = LittleEndian.readUInt16(extra, cursor + 2);
            cursor += 4;
            if (cursor + size > extra.length) {
                return null;
            }
            if (headerId == id) {
                return slice(extra, cursor, size);
            }
            cursor += size;
        }
        return null;
    }
}
