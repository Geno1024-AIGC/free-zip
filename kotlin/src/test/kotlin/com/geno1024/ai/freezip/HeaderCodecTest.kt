package com.geno1024.ai.freezip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeaderCodecTest {

    private fun sample(): StoredEntry = StoredEntry(
        name = "dir/file.txt",
        method = CompressionMethod.DEFLATED,
        crc32 = 0x11223344L,
        compressedSize = 100,
        uncompressedSize = 250,
        dosTime = MsDosDateTime(0x1234, 0x5678),
        hostSystem = ZipFormat.HOST_UNIX,
        externalAttributes = 0b111101101 shl 16,
        comment = "a note",
    )

    @Test
    fun localFileHeaderRoundTrip() {
        val entry = sample()
        val parsed = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize())

        assertEquals(entry.name, parsed.name)
        assertEquals(entry.method, parsed.method)
        assertEquals(entry.crc32, parsed.crc32)
        assertEquals(entry.compressedSize, parsed.compressedSize)
        assertEquals(entry.uncompressedSize, parsed.uncompressedSize)
        assertEquals(entry.dosTime, parsed.dosTime)
    }

    @Test
    fun centralDirectoryHeaderRoundTrip() {
        val entry = sample()
        val offset = 123456L
        val parsed = CentralDirectoryFileHeader.parse(CentralDirectoryFileHeader.of(entry, offset).serialize())

        assertEquals(entry.name, parsed.name)
        assertEquals(offset, parsed.localHeaderOffset)
        assertEquals(entry.externalAttributes, parsed.externalAttributes)
        assertEquals(entry.comment, parsed.comment)
    }

    @Test
    fun zip64SizesAndOffsetRoundTrip() {
        val uncompressed = 5_000_000_000L
        val compressed = 4_500_000_000L
        val entry = StoredEntry(
            name = "big.bin",
            method = CompressionMethod.DEFLATED,
            crc32 = 1,
            compressedSize = compressed,
            uncompressedSize = uncompressed,
            dosTime = MsDosDateTime(0, 0),
            hostSystem = ZipFormat.HOST_UNIX,
            externalAttributes = 0,
            comment = null,
        )

        val parsedLocal = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize())
        assertEquals(compressed, parsedLocal.compressedSize)
        assertEquals(uncompressed, parsedLocal.uncompressedSize)

        val offset = 6_000_000_000L
        val parsedCentral = CentralDirectoryFileHeader.parse(CentralDirectoryFileHeader.of(entry, offset).serialize())
        assertEquals(compressed, parsedCentral.compressedSize)
        assertEquals(uncompressed, parsedCentral.uncompressedSize)
        assertEquals(offset, parsedCentral.localHeaderOffset)
    }

    @Test
    fun utf8FlagIsSetForNonAsciiNames() {
        val entry = StoredEntry(
            name = "你好.txt",
            method = CompressionMethod.STORED,
            crc32 = 0,
            compressedSize = 0,
            uncompressedSize = 0,
            dosTime = MsDosDateTime(0, 0),
            hostSystem = ZipFormat.HOST_UNIX,
            externalAttributes = 0,
            comment = "",
        )

        assertTrue(entry.flags and GeneralPurposeBitFlag.UTF8 != 0)
        val parsed = LocalFileHeader.parse(LocalFileHeader.of(entry).serialize())
        assertEquals("你好.txt", parsed.name)
    }

    @Test
    fun dataDescriptorFlagIsNeverSet() {
        assertTrue(sample().flags and GeneralPurposeBitFlag.DATA_DESCRIPTOR == 0)
    }
}
