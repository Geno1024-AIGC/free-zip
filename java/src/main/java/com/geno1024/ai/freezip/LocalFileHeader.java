package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The local file header. APPNOTE.TXT §4.3.7.
 *
 * <p>One of these immediately precedes each entry's file data (§4.3.8). The
 * record holds the <em>effective</em> sizes: when either exceeds 32 bits the
 * serialized 32-bit fields saturate to {@code 0xFFFFFFFF} and the true values
 * travel in the Zip64 extended information extra field (§4.5.3).
 *
 * <p>The general purpose bit 3 (data descriptor) is never set, so no trailing
 * descriptor is needed.
 */
public record LocalFileHeader(
        int versionNeeded,
        int flags,
        CompressionMethod method,
        MsDosDateTime dosTime,
        long crc32,
        long compressedSize,
        long uncompressedSize,
        String name,
        byte[] extra
) {

    /** Builds the header for {@code entry}. */
    public static LocalFileHeader of(StoredEntry entry) {
        boolean zip64 = entry.needsZip64Sizes();
        // §4.5.3: the local Zip64 extra must carry BOTH original and compressed size.
        byte[] extra = zip64
                ? Zip64ExtendedInformationExtraField.serialize(
                        entry.uncompressedSize(), entry.compressedSize(), 0, 0, true, true, false, false)
                : new byte[0];
        return new LocalFileHeader(
                entry.versionNeeded(), entry.flags(), entry.method(), entry.dosTime(), entry.crc32(),
                entry.compressedSize(), entry.uncompressedSize(), entry.name(), extra);
    }

    /** True when either 32-bit size field saturates. */
    public boolean needsZip64() {
        return compressedSize >= ZipFormat.UINT32_MAX || uncompressedSize >= ZipFormat.UINT32_MAX;
    }

    /** Serializes the header, saturating the 32-bit size fields for Zip64. */
    public byte[] serialize() {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        boolean zip64 = needsZip64();

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.LOCAL_FILE_HEADER_FIXED_SIZE + nameBytes.length + extra.length);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE);
            LittleEndian.writeUInt16(out, versionNeeded);
            LittleEndian.writeUInt16(out, flags);
            LittleEndian.writeUInt16(out, method.id());
            LittleEndian.writeUInt16(out, dosTime.time());
            LittleEndian.writeUInt16(out, dosTime.date());
            LittleEndian.writeUInt32(out, crc32);
            LittleEndian.writeUInt32(out, zip64 ? ZipFormat.UINT32_MAX : compressedSize);
            LittleEndian.writeUInt32(out, zip64 ? ZipFormat.UINT32_MAX : uncompressedSize);
            LittleEndian.writeUInt16(out, nameBytes.length);
            LittleEndian.writeUInt16(out, extra.length);
            out.writeBytes(nameBytes);
            out.writeBytes(extra);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }

    /** Parses a local file header, resolving Zip64 sizes. */
    public static LocalFileHeader parse(byte[] bytes) {
        requireSignature(bytes, ZipFormat.LOCAL_FILE_HEADER_SIGNATURE);
        int versionNeeded = LittleEndian.readUInt16(bytes, 4);
        int flags = LittleEndian.readUInt16(bytes, 6);
        CompressionMethod method = CompressionMethod.fromId(LittleEndian.readUInt16(bytes, 8));
        MsDosDateTime dosTime = new MsDosDateTime(
                LittleEndian.readUInt16(bytes, 10), LittleEndian.readUInt16(bytes, 12));
        long crc32 = LittleEndian.readUInt32(bytes, 14);
        long compressedSize = LittleEndian.readUInt32(bytes, 18);
        long uncompressedSize = LittleEndian.readUInt32(bytes, 22);
        int nameLength = LittleEndian.readUInt16(bytes, 26);
        int extraLength = LittleEndian.readUInt16(bytes, 28);
        String name = new String(bytes, 30, nameLength, charsetFor(flags));
        byte[] extra = new byte[extraLength];
        System.arraycopy(bytes, 30 + nameLength, extra, 0, extraLength);

        byte[] zip64 = ExtraField.find(extra, Zip64ExtendedInformationExtraField.HEADER_ID);
        if (zip64 != null) {
            int cursor = 0;
            if (uncompressedSize == ZipFormat.UINT32_MAX) {
                uncompressedSize = LittleEndian.readUInt64(zip64, cursor);
                cursor += 8;
            }
            if (compressedSize == ZipFormat.UINT32_MAX) {
                compressedSize = LittleEndian.readUInt64(zip64, cursor);
            }
        }
        return new LocalFileHeader(versionNeeded, flags, method, dosTime, crc32,
                compressedSize, uncompressedSize, name, extra);
    }

    private static void requireSignature(byte[] bytes, long expected) {
        if (bytes.length < 4 || LittleEndian.readUInt32(bytes, 0) != expected) {
            throw new IllegalArgumentException("not a local file header (unexpected signature)");
        }
    }

    private static Charset charsetFor(int flags) {
        return (flags & GeneralPurposeBitFlag.UTF8) != 0
                ? StandardCharsets.UTF_8
                : StandardCharsets.ISO_8859_1;
    }
}
