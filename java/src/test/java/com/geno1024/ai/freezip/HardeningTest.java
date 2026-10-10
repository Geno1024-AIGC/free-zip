package com.geno1024.ai.freezip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HardeningTest {

    @TempDir
    Path temp;

    private Path pack(String name, byte[] content) throws IOException {
        return pack(name, content, CompressionMethod.DEFLATED);
    }

    private Path pack(String name, byte[] content, CompressionMethod method) throws IOException {
        Path input = temp.resolve("in-" + name);
        Files.write(input, content);
        Path prefix = temp.resolve("store/" + name);
        Files.createDirectories(prefix.getParent());
        Packer.pack(input, prefix, PackOptions.defaults(name).withMethod(method));
        return prefix;
    }

    private Path assemble(List<Path> keys, AssembleOptions options) throws IOException {
        Path zip = temp.resolve("out-" + System.nanoTime() + ".zip");
        try (OutputStream out = Files.newOutputStream(zip)) {
            Assembler.assemble(keys, out, options);
        }
        return zip;
    }

    @Test
    void entryNameTooLongIsRejected() {
        String name = "x".repeat(ZipFormat.UINT16_MAX + 1);
        assertThrows(IllegalArgumentException.class, () -> sampleEntry(name, null));
    }

    @Test
    void entryCommentTooLongIsRejected() {
        String comment = "c".repeat(ZipFormat.UINT16_MAX + 1);
        assertThrows(IllegalArgumentException.class, () -> sampleEntry("ok.txt", comment));
    }

    @Test
    void archiveCommentTooLongIsRejected() {
        String comment = "c".repeat(ZipFormat.UINT16_MAX + 1);
        assertThrows(IllegalArgumentException.class,
                () -> new AssembleOptions(AssembleOptions.Zip64Mode.AUTO, comment));
    }

    @Test
    void duplicateEntryNamesAreRejected() throws IOException {
        Path first = pack("dup.txt", "one".getBytes(StandardCharsets.UTF_8));
        Path second = pack("other.bin", "two".getBytes(StandardCharsets.UTF_8));
        EntryEditor.edit(second, List.of(new EntryOperation.Rename("dup.txt")));

        IOException error = assertThrows(IOException.class,
                () -> assemble(List.of(first, second), AssembleOptions.defaults()));
        assertTrue(error.getMessage().contains("duplicate entry name"));
    }

    @Test
    void verifyAcceptsValidEntry() throws IOException {
        Path key = pack("ok.txt", "hello".getBytes(StandardCharsets.UTF_8), CompressionMethod.STORED);
        StoredEntry entry = Verifier.verify(key);
        assertEquals("ok.txt", entry.name());
    }

    @Test
    void verifyDetectsCrcMismatch() throws IOException {
        Path key = pack("crc.txt", "hello".getBytes(StandardCharsets.UTF_8), CompressionMethod.STORED);
        Files.write(StoredEntryFiles.data(key), "worlo".getBytes(StandardCharsets.UTF_8));

        IOException error = assertThrows(IOException.class, () -> Verifier.verify(key));
        assertTrue(error.getMessage().contains("CRC-32 mismatch"));
    }

    @Test
    void verifyDetectsSizeMismatch() throws IOException {
        Path key = pack("size.txt", "hello".getBytes(StandardCharsets.UTF_8), CompressionMethod.STORED);
        Files.write(StoredEntryFiles.data(key), new byte[]{1, 2});

        IOException error = assertThrows(IOException.class, () -> Verifier.verify(key));
        assertTrue(error.getMessage().contains("size mismatch"));
    }

    @Test
    void verifyAcceptsDeflatedEntry() throws IOException {
        byte[] content = "hello, free-zip!\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        Path key = pack("big.txt", content, CompressionMethod.DEFLATED);
        assertEquals("big.txt", Verifier.verify(key).name());
    }

    @Test
    void zip64NeverRejectsOverflowWithoutCopyingPayload() throws IOException {
        long huge = 4_500_000_000L;
        StoredEntry entry = new StoredEntry(
                "big.bin", CompressionMethod.STORED, 0L, huge, huge,
                MsDosDateTime.of(LocalDateTime.of(2020, 1, 1, 0, 0)),
                ZipFormat.HOST_UNIX, 0, null);
        Path key = temp.resolve("big");
        Files.createDirectories(key);
        Files.write(StoredEntryFiles.localHeader(key), LocalFileHeader.of(entry).serialize());
        Files.write(StoredEntryFiles.centralHeader(key), CentralDirectoryFileHeader.of(entry, 0).serialize());
        try (RandomAccessFile raf = new RandomAccessFile(StoredEntryFiles.data(key).toFile(), "rw")) {
            raf.setLength(huge);
        }

        assertTrue(Assembler.estimateSize(List.of(key), AssembleOptions.defaults()) > huge);

        IOException error = assertThrows(IOException.class, () -> Assembler.estimateSize(
                List.of(key), new AssembleOptions(AssembleOptions.Zip64Mode.NEVER, "")));
        assertTrue(error.getMessage().contains("--zip64 never"));
    }

    private static StoredEntry sampleEntry(String name, String comment) {
        return new StoredEntry(
                name, CompressionMethod.STORED, 0L, 3L, 3L,
                MsDosDateTime.of(LocalDateTime.of(2020, 1, 1, 0, 0)),
                ZipFormat.HOST_UNIX, UnixMode.externalAttributes(0100644), comment);
    }
}