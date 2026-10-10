package com.geno1024.ai.freezip

import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class HardeningTest {

    @TempDir
    lateinit var temp: Path

    private fun pack(name: String, content: ByteArray, method: CompressionMethod = CompressionMethod.DEFLATED): Path {
        val input = temp.resolve("in-$name")
        Files.write(input, content)
        val prefix = temp.resolve("store/$name")
        Files.createDirectories(prefix.parent)
        Packer.pack(input, prefix, PackOptions.defaults(name).withMethod(method))
        return prefix
    }

    private fun assemble(keys: List<Path>, options: AssembleOptions): Path {
        val zip = temp.resolve("out-${System.nanoTime()}.zip")
        Files.newOutputStream(zip).use { out: OutputStream ->
            Assembler.assemble(keys, out, options)
        }
        return zip
    }

    @Test
    fun entryNameTooLongIsRejected() {
        val name = "x".repeat(ZipFormat.UINT16_MAX + 1)
        assertThrows(IllegalArgumentException::class.java) { sampleEntry(name, null) }
    }

    @Test
    fun entryCommentTooLongIsRejected() {
        val comment = "c".repeat(ZipFormat.UINT16_MAX + 1)
        assertThrows(IllegalArgumentException::class.java) { sampleEntry("ok.txt", comment) }
    }

    @Test
    fun archiveCommentTooLongIsRejected() {
        val comment = "c".repeat(ZipFormat.UINT16_MAX + 1)
        assertThrows(IllegalArgumentException::class.java) {
            AssembleOptions(AssembleOptions.Zip64Mode.AUTO, comment)
        }
    }

    @Test
    fun duplicateEntryNamesAreRejected() {
        val first = pack("dup.txt", "one".toByteArray(StandardCharsets.UTF_8))
        val second = pack("other.bin", "two".toByteArray(StandardCharsets.UTF_8))
        EntryEditor.edit(second, listOf(EntryOperation.Rename("dup.txt")))

        val error = assertThrows(java.io.IOException::class.java) {
            assemble(listOf(first, second), AssembleOptions.defaults())
        }
        assertTrue(error.message!!.contains("duplicate entry name"))
    }

    @Test
    fun verifyAcceptsValidEntry() {
        val key = pack("ok.txt", "hello".toByteArray(StandardCharsets.UTF_8), CompressionMethod.STORED)
        assertEquals("ok.txt", Verifier.verify(key).name)
    }

    @Test
    fun verifyDetectsCrcMismatch() {
        val key = pack("crc.txt", "hello".toByteArray(StandardCharsets.UTF_8), CompressionMethod.STORED)
        Files.write(StoredEntryFiles.data(key), "worlo".toByteArray(StandardCharsets.UTF_8))

        val error = assertThrows(java.io.IOException::class.java) { Verifier.verify(key) }
        assertTrue(error.message!!.contains("CRC-32 mismatch"))
    }

    @Test
    fun verifyDetectsSizeMismatch() {
        val key = pack("size.txt", "hello".toByteArray(StandardCharsets.UTF_8), CompressionMethod.STORED)
        Files.write(StoredEntryFiles.data(key), byteArrayOf(1, 2))

        val error = assertThrows(java.io.IOException::class.java) { Verifier.verify(key) }
        assertTrue(error.message!!.contains("size mismatch"))
    }

    @Test
    fun verifyAcceptsDeflatedEntry() {
        val content = "hello, free-zip!\n".repeat(100).toByteArray(StandardCharsets.UTF_8)
        val key = pack("big.txt", content, CompressionMethod.DEFLATED)
        assertEquals("big.txt", Verifier.verify(key).name)
    }

    @Test
    fun zip64NeverRejectsOverflowWithoutCopyingPayload() {
        val huge = 4_500_000_000L
        val entry = StoredEntry(
            "big.bin", CompressionMethod.STORED, 0L, huge, huge,
            MsDosDateTime.of(LocalDateTime.of(2020, 1, 1, 0, 0)),
            ZipFormat.HOST_UNIX, 0, null,
        )
        val key = temp.resolve("big")
        Files.createDirectories(key)
        Files.write(StoredEntryFiles.localHeader(key), LocalFileHeader.of(entry).serialize())
        Files.write(StoredEntryFiles.centralHeader(key), CentralDirectoryFileHeader.of(entry, 0).serialize())
        RandomAccessFile(StoredEntryFiles.data(key).toFile(), "rw").use { raf ->
            raf.setLength(huge)
        }

        assertTrue(Assembler.estimateSize(listOf(key), AssembleOptions.defaults()) > huge)

        val error = assertThrows(java.io.IOException::class.java) {
            Assembler.estimateSize(listOf(key), AssembleOptions(AssembleOptions.Zip64Mode.NEVER, ""))
        }
        assertTrue(error.message!!.contains("--zip64 never"))
    }

    private fun sampleEntry(name: String, comment: String?): StoredEntry = StoredEntry(
        name, CompressionMethod.STORED, 0L, 3L, 3L,
        MsDosDateTime.of(LocalDateTime.of(2020, 1, 1, 0, 0)),
        ZipFormat.HOST_UNIX, UnixMode.externalAttributes(0b111101101), comment,
    )
}