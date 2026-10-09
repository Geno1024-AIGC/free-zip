package com.geno1024.ai.freezip;

/**
 * A parsed central directory file header. APPNOTE.TXT §4.3.12.
 *
 * <p>Zip64 size and offset fields have already been resolved.
 */
public record CentralDirectoryFileHeader(
        int versionMadeBy,
        int versionNeeded,
        int flags,
        CompressionMethod method,
        DosTime dosTime,
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
}
