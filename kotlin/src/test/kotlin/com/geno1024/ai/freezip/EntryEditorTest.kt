package com.geno1024.ai.freezip

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EntryEditorTest {

    @TempDir
    lateinit var temp: Path

    private fun pack(name: String, content: String): Path {
        val input = temp.resolve("in-$name")
        Files.writeString(input, content)
        val prefix = temp.resolve("store/$name")
        Files.createDirectories(prefix.parent)
        Packer.pack(input, prefix, PackOptions.defaults(name))
        return prefix
    }

    private fun assemble(keys: List<Path>): Path {
        val zip = temp.resolve("out-${System.nanoTime()}.zip")
        Files.newOutputStream(zip).use { out: OutputStream ->
            Assembler.assemble(keys, out, AssembleOptions.defaults())
        }
        return zip
    }

    @Test
    fun setCommentOnlyTouchesCentralDirectory() {
        val key = pack("a.txt", "alpha")

        val localBefore = Files.readAllBytes(StoredEntryFiles.localHeader(key))
        EntryEditor.edit(key, listOf(EntryOperation.SetComment("hello")))

        assertTrue(localBefore.contentEquals(Files.readAllBytes(StoredEntryFiles.localHeader(key))))

        ZipFile(assemble(listOf(key)).toFile()).use { zip ->
            val entry = zip.getEntry("a.txt")
            assertNotNull(entry)
            assertEquals("hello", entry.comment)
            assertEquals("alpha", String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    @Test
    fun renameUpdatesBothHeaders() {
        val key = pack("old.txt", "payload")
        EntryEditor.edit(key, listOf(EntryOperation.Rename("new.txt")))

        ZipFile(assemble(listOf(key)).toFile()).use { zip ->
            assertNull(zip.getEntry("old.txt"))
            val entry: ZipEntry = zip.getEntry("new.txt")
            assertNotNull(entry)
            assertEquals("payload", String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    @Test
    fun setModificationTimeRoundTrips() {
        val key = pack("t.txt", "x")
        val time = LocalDateTime.of(2001, 2, 3, 4, 5, 6)

        val entry = EntryEditor.edit(key, listOf(EntryOperation.SetModificationTime(time)))

        assertEquals(MsDosDateTime.of(time), entry.dosTime)
        assertEquals(MsDosDateTime.of(time), EntryEditor.load(key).dosTime)
    }

    @Test
    fun setUnixModeUpdatesExternalAttributes() {
        val key = pack("m.txt", "x")
        val mode = 0b111101101

        val entry = EntryEditor.edit(key, listOf(EntryOperation.SetUnixMode(mode)))

        assertEquals(ZipFormat.HOST_UNIX, entry.hostSystem)
        assertEquals(UnixMode.externalAttributes(mode), entry.externalAttributes)
    }
}
