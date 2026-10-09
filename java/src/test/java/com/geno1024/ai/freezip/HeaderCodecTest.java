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
                new DosTime(0x1234, 0x5678),
                ZipFormat.HOST_UNIX,
                0100755 << 16,
                "a note");
    }

    @Test
    void localFileHeaderRoundTrip() {
        StoredEntry entry = sample();
        LocalFileHeader parsed = HeaderParser.parseLocalFileHeader(HeaderCodec.localFileHeader(entry));

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
        CentralDirectoryFileHeader parsed = HeaderParser.parseCentralDirectoryFileHeader(
                HeaderCodec.centralDirectoryFileHeader(entry, offset));

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
                new DosTime(0, 0), ZipFormat.HOST_UNIX, 0, null);

        LocalFileHeader parsedLocal = HeaderParser.parseLocalFileHeader(HeaderCodec.localFileHeader(entry));
        assertEquals(compressed, parsedLocal.compressedSize());
        assertEquals(uncompressed, parsedLocal.uncompressedSize());

        long offset = 6_000_000_000L;
        CentralDirectoryFileHeader parsedCentral = HeaderParser.parseCentralDirectoryFileHeader(
                HeaderCodec.centralDirectoryFileHeader(entry, offset));
        assertEquals(compressed, parsedCentral.compressedSize());
        assertEquals(uncompressed, parsedCentral.uncompressedSize());
        assertEquals(offset, parsedCentral.localHeaderOffset());
    }

    @Test
    void utf8FlagIsSetForNonAsciiNames() {
        StoredEntry entry = new StoredEntry(
                "你好.txt", CompressionMethod.STORED, 0, 0, 0,
                new DosTime(0, 0), ZipFormat.HOST_UNIX, 0, "");

        assertTrue((entry.flags() & ZipFormat.FLAG_UTF8) != 0);
        LocalFileHeader parsed = HeaderParser.parseLocalFileHeader(HeaderCodec.localFileHeader(entry));
        assertEquals("你好.txt", parsed.name());
    }

    @Test
    void dataDescriptorFlagIsNeverSet() {
        assertTrue((sample().flags() & ZipFormat.FLAG_DATA_DESCRIPTOR) == 0);
    }
}
