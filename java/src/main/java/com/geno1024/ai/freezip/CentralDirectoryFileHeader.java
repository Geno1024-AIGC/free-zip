package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The central directory file header. APPNOTE.TXT §4.3.12.
 *
 * <p>Collectively these headers form the central directory, the archive's index.
 * The only field that cannot be known when an entry is packed is the "relative
 * offset of local header", supplied when the archive is assembled. A local header
 * offset that no longer fits in 32 bits travels in the Zip64 extended information
 * extra field (§4.5.3).
 */
public record CentralDirectoryFileHeader(
        int versionMadeBy,
        int versionNeeded,
        int flags,
        CompressionMethod method,
        MsDosDateTime dosTime,
        long crc32,
        long compressedSize,
        long uncompressedSize,
        int diskNumberStart,
        int internalAttributes,
        int externalAttributes,
        long localHeaderOffset,
        String name,
        byte[] extra,
        String comment
) {

    /** Builds the header for {@code entry} located at {@code localHeaderOffset}. */
    public static CentralDirectoryFileHeader of(StoredEntry entry, long localHeaderOffset) {
        boolean needUncompressed = entry.uncompressedSize() >= ZipFormat.UINT32_MAX;
        boolean needCompressed = entry.compressedSize() >= ZipFormat.UINT32_MAX;
        boolean needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX;
        byte[] extra = Zip64ExtendedInformationExtraField.serialize(
                entry.uncompressedSize(), entry.compressedSize(), localHeaderOffset, 0,
                needUncompressed, needCompressed, needOffset, false);

        int versionNeeded = entry.method().versionNeeded();
        if (needUncompressed || needCompressed || needOffset) {
            versionNeeded = Math.max(versionNeeded, ZipFormat.VERSION_NEEDED_ZIP64);
        }
        return new CentralDirectoryFileHeader(
                entry.versionMadeBy(), versionNeeded, entry.flags(), entry.method(), entry.dosTime(),
                entry.crc32(), entry.compressedSize(), entry.uncompressedSize(), 0, 0,
                entry.externalAttributes(), localHeaderOffset, entry.name(), extra, entry.comment());
    }

    /** Serializes the header, saturating size/offset fields for Zip64. */
    public byte[] serialize() {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] commentBytes = comment == null ? new byte[0] : comment.getBytes(StandardCharsets.UTF_8);
        boolean needUncompressed = uncompressedSize >= ZipFormat.UINT32_MAX;
        boolean needCompressed = compressedSize >= ZipFormat.UINT32_MAX;
        boolean needOffset = localHeaderOffset >= ZipFormat.UINT32_MAX;

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE
                        + nameBytes.length + extra.length + commentBytes.length);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
            LittleEndian.writeUInt16(out, versionMadeBy);
            LittleEndian.writeUInt16(out, versionNeeded);
            LittleEndian.writeUInt16(out, flags);
            LittleEndian.writeUInt16(out, method.id());
            LittleEndian.writeUInt16(out, dosTime.time());
            LittleEndian.writeUInt16(out, dosTime.date());
            LittleEndian.writeUInt32(out, crc32);
            LittleEndian.writeUInt32(out, needCompressed ? ZipFormat.UINT32_MAX : compressedSize);
            LittleEndian.writeUInt32(out, needUncompressed ? ZipFormat.UINT32_MAX : uncompressedSize);
            LittleEndian.writeUInt16(out, nameBytes.length);
            LittleEndian.writeUInt16(out, extra.length);
            LittleEndian.writeUInt16(out, commentBytes.length);
            LittleEndian.writeUInt16(out, diskNumberStart);
            LittleEndian.writeUInt16(out, internalAttributes);
            LittleEndian.writeUInt32(out, externalAttributes & ZipFormat.UINT32_MAX);
            LittleEndian.writeUInt32(out, needOffset ? ZipFormat.UINT32_MAX : localHeaderOffset);
            out.writeBytes(nameBytes);
            out.writeBytes(extra);
            out.writeBytes(commentBytes);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }

    /** Parses a central directory file header, resolving Zip64 fields. */
    public static CentralDirectoryFileHeader parse(byte[] bytes) {
        requireSignature(bytes, ZipFormat.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE);
        int versionMadeBy = LittleEndian.readUInt16(bytes, 4);
        int versionNeeded = LittleEndian.readUInt16(bytes, 6);
        int flags = LittleEndian.readUInt16(bytes, 8);
        CompressionMethod method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 10));
        MsDosDateTime dosTime = new MsDosDateTime(
                LittleEndian.readUInt16(bytes, 12), LittleEndian.readUInt16(bytes, 14));
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
        byte[] extra = new byte[extraLength];
        System.arraycopy(bytes, cursor, extra, 0, extraLength);
        cursor += extraLength;
        String comment = new String(bytes, cursor, commentLength, charsetFor(flags));

        byte[] zip64 = ExtraField.find(extra, Zip64ExtendedInformationExtraField.HEADER_ID);
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
        return new CentralDirectoryFileHeader(versionMadeBy, versionNeeded, flags, method, dosTime, crc32,
                compressedSize, uncompressedSize, diskNumberStart, internalAttributes, externalAttributes,
                localHeaderOffset, name, extra, comment);
    }

    private static void requireSignature(byte[] bytes, long expected) {
        if (bytes.length < 4 || LittleEndian.readUInt32(bytes, 0) != expected) {
            throw new IllegalArgumentException("not a central directory file header (unexpected signature)");
        }
    }

    private static Charset charsetFor(int flags) {
        return (flags & GeneralPurposeBitFlag.UTF8) != 0
                ? StandardCharsets.UTF_8
                : StandardCharsets.ISO_8859_1;
    }
}
