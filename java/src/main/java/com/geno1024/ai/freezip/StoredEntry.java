package com.geno1024.ai.freezip;

import java.nio.charset.StandardCharsets;

/**
 * Immutable metadata describing one stored entry.
 *
 * <p>This is the in-memory shape of a {@code .lfh} / {@code .cdfh} pair: every
 * value needed to emit both headers. The compressed payload itself lives in the
 * matching {@code .data} file and is referenced by {@link #compressedSize()}.
 *
 * <p>Field semantics follow PKWARE APPNOTE.TXT: {@code name} is the file name
 * stored in the header (APPNOTE.TXT §4.3.7 / §4.3.12), {@code hostSystem} and
 * {@code externalAttributes} populate "version made by" and "external file
 * attributes" (§4.4.2, §4.4.15), and {@code dosTime} follows §4.4.6.
 */
public record StoredEntry(
        String name,
        CompressionMethod method,
        long crc32,
        long compressedSize,
        long uncompressedSize,
        MsDosDateTime dosTime,
        int hostSystem,
        int externalAttributes,
        String comment
) {

    public StoredEntry {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("entry name must not be empty");
        }
        if (method == null) {
            throw new IllegalArgumentException("compression method must not be null");
        }
        if (crc32 < 0 || crc32 > ZipFormat.UINT32_MAX) {
            throw new IllegalArgumentException("crc32 out of range: " + crc32);
        }
        if (compressedSize < 0 || uncompressedSize < 0) {
            throw new IllegalArgumentException("sizes must not be negative");
        }
        if (dosTime == null) {
            throw new IllegalArgumentException("dosTime must not be null");
        }
    }

    /**
     * Combines the two parsed headers, validating that the duplicated fields
     * agree. Inverse of {@link LocalFileHeader#of} / {@link CentralDirectoryFileHeader#of}.
     */
    public static StoredEntry of(LocalFileHeader local, CentralDirectoryFileHeader central) {
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

    /** General purpose bit flag. APPNOTE.TXT §4.4.4 (bit 11 = UTF-8 name). */
    public int flags() {
        return isAscii(name) && isAscii(comment) ? 0 : GeneralPurposeBitFlag.UTF8;
    }

    /** True when either 32-bit size field would overflow and Zip64 is required. */
    public boolean needsZip64Sizes() {
        return uncompressedSize >= ZipFormat.UINT32_MAX
                || compressedSize >= ZipFormat.UINT32_MAX;
    }

    /** Minimum "version needed to extract". APPNOTE.TXT §4.4.3. */
    public int versionNeeded() {
        int version = method.versionNeeded();
        if (needsZip64Sizes()) {
            version = Math.max(version, ZipFormat.VERSION_NEEDED_ZIP64);
        }
        return version;
    }

    /** "Version made by": host system byte plus a 2.0 spec timestamp. §4.4.2. */
    public int versionMadeBy() {
        return ((hostSystem & 0xFF) << 8) | versionNeeded();
    }

    public byte[] nameBytes() {
        return name.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] commentBytes() {
        return comment == null ? new byte[0] : comment.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isAscii(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
