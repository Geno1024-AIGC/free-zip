package com.geno1024.ai.freezip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryEditorTest {

    @TempDir
    Path temp;

    private Path pack(String name, String content) throws IOException {
        Path input = temp.resolve("in-" + name);
        Files.writeString(input, content);
        Path prefix = temp.resolve("store/" + name);
        Files.createDirectories(prefix.getParent());
        Packer.pack(input, prefix, PackOptions.defaults(name));
        return prefix;
    }

    private Path assemble(List<Path> keys) throws IOException {
        Path zip = temp.resolve("out-" + System.nanoTime() + ".zip");
        try (OutputStream out = Files.newOutputStream(zip)) {
            Assembler.assemble(keys, out, AssembleOptions.defaults());
        }
        return zip;
    }

    @Test
    void setCommentOnlyTouchesCentralDirectory() throws IOException {
        Path key = pack("a.txt", "alpha");

        byte[] localBefore = Files.readAllBytes(StoredEntryFiles.localHeader(key));
        EntryEditor.edit(key, List.of(new EntryOperation.SetComment("hello")));

        assertTrue(java.util.Arrays.equals(localBefore,
                Files.readAllBytes(StoredEntryFiles.localHeader(key))));

        try (ZipFile zip = new ZipFile(assemble(List.of(key)).toFile())) {
            ZipEntry entry = zip.getEntry("a.txt");
            assertNotNull(entry);
            assertEquals("hello", entry.getComment());
            assertEquals("alpha", new String(zip.getInputStream(entry).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    void renameUpdatesBothHeaders() throws IOException {
        Path key = pack("old.txt", "payload");
        EntryEditor.edit(key, List.of(new EntryOperation.Rename("new.txt")));

        try (ZipFile zip = new ZipFile(assemble(List.of(key)).toFile())) {
            assertNull(zip.getEntry("old.txt"));
            ZipEntry entry = zip.getEntry("new.txt");
            assertNotNull(entry);
            assertEquals("payload", new String(zip.getInputStream(entry).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    void setModificationTimeRoundTrips() throws IOException {
        Path key = pack("t.txt", "x");
        LocalDateTime time = LocalDateTime.of(2001, 2, 3, 4, 5, 6);

        StoredEntry entry = EntryEditor.edit(key, List.of(new EntryOperation.SetModificationTime(time)));

        assertEquals(MsDosDateTime.of(time), entry.dosTime());
        assertEquals(MsDosDateTime.of(time), EntryEditor.load(key).dosTime());
    }

    @Test
    void setUnixModeUpdatesExternalAttributes() throws IOException {
        Path key = pack("m.txt", "x");
        int mode = 0755;

        StoredEntry entry = EntryEditor.edit(key, List.of(new EntryOperation.SetUnixMode(mode)));

        assertEquals(ZipFormat.HOST_UNIX, entry.hostSystem());
        assertEquals(UnixMode.externalAttributes(mode), entry.externalAttributes());
    }
}
