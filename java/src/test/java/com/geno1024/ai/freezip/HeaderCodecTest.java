package com.geno1024.ai.freezip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeaderCodecTest {

    private static StoredEntry sample() {
        return new StoredEntry(
                "dir/file.txt",
                CompressionMethod.DEFLATED,
                0x11223344L,
                100,
                250,
                new MsDosDateTime(0x1234, 0x5678),
                ZipFormat.HOST_UNIX,
                0100755 << 16,
                "a note");
    }

    @Test
    void localFileHeaderRoundTrip() {
        StoredEntry entry = sample();
        LocalFileHeader parsed = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize());

        assertEquals(entry.name(), parsed.name());
        assertEquals(entry.method(), parsed.method());
        assertEquals(entry.crc32(), parsed.crc32());
        assertEquals(entry.compressedSize(), parsed.compressedSize());
        assertEquals(entry.uncompressedSize(), parsed.uncompressedSize());
        assertEquals(entry.dosTime(), parsed.dosTime());
    }

    @Test
    void centralDirectoryHeaderRoundTrip() {
        StoredEntry entry = sample();
        long offset = 123456;
        CentralDirectoryFileHeader parsed = CentralDirectoryFileHeader.parse(
                CentralDirectoryFileHeader.of(entry, offset).serialize());

        assertEquals(entry.name(), parsed.name());
        assertEquals(offset, parsed.localHeaderOffset());
        assertEquals(entry.externalAttributes(), parsed.externalAttributes());
        assertEquals(entry.comment(), parsed.comment());
    }

    @Test
    void zip64SizesAndOffsetRoundTrip() {
        long uncompressed = 5_000_000_000L;
        long compressed = 4_500_000_000L;
        StoredEntry entry = new StoredEntry(
                "big.bin", CompressionMethod.DEFLATED, 1, compressed, uncompressed,
                new MsDosDateTime(0, 0), ZipFormat.HOST_UNIX, 0, null);

        LocalFileHeader parsedLocal = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize());
        assertEquals(compressed, parsedLocal.compressedSize());
        assertEquals(uncompressed, parsedLocal.uncompressedSize());

        long offset = 6_000_000_000L;
        CentralDirectoryFileHeader parsedCentral = CentralDirectoryFileHeader.parse(
                CentralDirectoryFileHeader.of(entry, offset).serialize());
        assertEquals(compressed, parsedCentral.compressedSize());
        assertEquals(uncompressed, parsedCentral.uncompressedSize());
        assertEquals(offset, parsedCentral.localHeaderOffset());
    }

    @Test
    void utf8FlagIsSetForNonAsciiNames() {
        StoredEntry entry = new StoredEntry(
                "你好.txt", CompressionMethod.STORED, 0, 0, 0,
                new MsDosDateTime(0, 0), ZipFormat.HOST_UNIX, 0, "");

        assertTrue((entry.flags() & GeneralPurposeBitFlag.UTF8) != 0);
        LocalFileHeader parsed = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize());
        assertEquals("你好.txt", parsed.name());
    }

    @Test
    void dataDescriptorFlagIsNeverSet() {
        assertTrue((sample().flags() & GeneralPurposeBitFlag.DATA_DESCRIPTOR) == 0);
    }
}
