package com.geno1024.ai.freezip;

/**
 * A parsed local file header. APPNOTE.TXT §4.3.7.
 *
 * <p>Zip64 size fields have already been resolved, so {@link #compressedSize()}
 * and {@link #uncompressedSize()} are the effective values.
 */
public record LocalFileHeader(
        int versionNeeded,
        int flags,
        CompressionMethod method,
        DosTime dosTime,
        long crc32,
        long compressedSize,
        long uncompressedSize,
        String name,
        byte[] extra
) {
}
