package com.geno1024.ai.freezip

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PackerAssemblerTest {

    @TempDir
    lateinit var temp: Path

    private fun pack(name: String, content: ByteArray, method: CompressionMethod): Path {
        val input = temp.resolve("in-$name")
        Files.write(input, content)
        val prefix = temp.resolve("store/$name")
        Files.createDirectories(prefix.parent)
        Packer.pack(input, prefix, PackOptions.defaults(name).withMethod(method))
        return prefix
    }

    private fun assemble(keys: List<Path>, options: AssembleOptions): Path {
        val zip = temp.resolve("out-${System.nanoTime()}.zip")
        Files.newOutputStream(zip).use { out ->
            Assembler.assemble(keys, out, options)
        }
        return zip
    }

    @Test
    fun deflatedRoundTripThroughZipFile() {
        val content = "hello, free-zip!\n".repeat(50).toByteArray(StandardCharsets.UTF_8)
        val key = pack("greeting.txt", content, CompressionMethod.DEFLATED)

        ZipFile(assemble(listOf(key), AssembleOptions.defaults()).toFile()).use { zip ->
            val entry = zip.getEntry("greeting.txt")
            assertNotNull(entry)
            assertTrue(content.contentEquals(zip.getInputStream(entry).readAllBytes()))
        }
    }

    @Test
    fun storedMethodRoundTrip() {
        val content = ByteArray(4096)
        Random(1).nextBytes(content)
        val key = pack("random.bin", content, CompressionMethod.STORED)

        ZipFile(assemble(listOf(key), AssembleOptions.defaults()).toFile()).use { zip ->
            assertTrue(content.contentEquals(zip.getInputStream(zip.getEntry("random.bin")).readAllBytes()))
        }
    }

    @Test
    fun sequentialReadWithZipInputStream() {
        val first = pack("a.txt", "alpha".toByteArray(StandardCharsets.UTF_8), CompressionMethod.DEFLATED)
        val second = pack("b.txt", "beta".toByteArray(StandardCharsets.UTF_8), CompressionMethod.DEFLATED)

        ZipInputStream(
            Files.newInputStream(assemble(listOf(first, second), AssembleOptions.defaults())),
        ).use { input ->
            var entry: ZipEntry = input.nextEntry
            assertEquals("a.txt", entry.name)
            assertEquals("alpha", String(input.readAllBytes(), StandardCharsets.UTF_8))
            entry = input.nextEntry
            assertEquals("b.txt", entry.name)
            assertEquals("beta", String(input.readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    @Test
    fun utf8NameRoundTrip() {
        val key = pack("问候.txt", "你好".toByteArray(StandardCharsets.UTF_8), CompressionMethod.DEFLATED)

        ZipFile(assemble(listOf(key), AssembleOptions.defaults()).toFile()).use { zip ->
            assertNotNull(zip.getEntry("问候.txt"))
        }
    }

    @Test
    fun unixModeReachesCentralDirectory() {
        val input = temp.resolve("script.sh")
        Files.writeString(input, "#!/bin/sh\n")
        val mode = UnixMode.read(input)
        val prefix = temp.resolve("store/script.sh")
        Files.createDirectories(prefix.parent)
        Packer.pack(input, prefix, PackOptions.defaults(null))

        val parsed = CentralDirectoryFileHeader.parse(Files.readAllBytes(StoredEntryFiles.centralHeader(prefix)))
        assertEquals(UnixMode.externalAttributes(mode), parsed.externalAttributes)
    }

    @Test
    fun estimateSizeMatchesAssembly() {
        val first = pack("one.txt", "one".toByteArray(StandardCharsets.UTF_8), CompressionMethod.DEFLATED)
        val second = pack("two.txt", "two".toByteArray(StandardCharsets.UTF_8), CompressionMethod.STORED)
        val keys = listOf(first, second)

        val zip = assemble(keys, AssembleOptions.defaults())
        assertEquals(Files.size(zip), Assembler.estimateSize(keys, AssembleOptions.defaults()))
    }

    @Test
    fun forcedZip64IsReadableAndEmitsZip64Records() {
        val key = pack("small.txt", "small".toByteArray(StandardCharsets.UTF_8), CompressionMethod.DEFLATED)
        val zip = assemble(listOf(key), AssembleOptions(AssembleOptions.Zip64Mode.ALWAYS, ""))

        val bytes = Files.readAllBytes(zip)
        assertTrue(containsSignature(bytes, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE))
        assertTrue(containsSignature(bytes, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE))

        ZipFile(zip.toFile()).use { zf ->
            assertTrue("small".toByteArray(StandardCharsets.UTF_8).contentEquals(
                zf.getInputStream(zf.getEntry("small.txt")).readAllBytes(),
            ))
        }
    }

    @Test
    fun payloadSizeMismatchIsRejected() {
        val key = pack("m.txt", "abc".toByteArray(StandardCharsets.UTF_8), CompressionMethod.STORED)
        Files.write(StoredEntryFiles.data(key), byteArrayOf(1, 2, 3, 4))

        val error = assertThrows(IOException::class.java) {
            assemble(listOf(key), AssembleOptions.defaults())
        }
        assertTrue(error.message!!.contains("payload size mismatch"))
    }

    private fun containsSignature(haystack: ByteArray, signature: Long): Boolean {
        val needle = LittleEndian.bytesUInt32(signature)
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
