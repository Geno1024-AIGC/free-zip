package com.geno1024.ai.freezip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackerAssemblerTest {

    @TempDir
    Path temp;

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
    void deflatedRoundTripThroughZipFile() throws IOException {
        byte[] content = "hello, free-zip!\n".repeat(50).getBytes(StandardCharsets.UTF_8);
        Path key = pack("greeting.txt", content, CompressionMethod.DEFLATED);

        try (ZipFile zip = new ZipFile(assemble(List.of(key), AssembleOptions.defaults()).toFile())) {
            ZipEntry entry = zip.getEntry("greeting.txt");
            assertNotNull(entry);
            assertArrayEquals(content, zip.getInputStream(entry).readAllBytes());
        }
    }

    @Test
    void storedMethodRoundTrip() throws IOException {
        byte[] content = new byte[4096];
        new java.util.Random(1).nextBytes(content);
        Path key = pack("random.bin", content, CompressionMethod.STORED);

        try (ZipFile zip = new ZipFile(assemble(List.of(key), AssembleOptions.defaults()).toFile())) {
            assertArrayEquals(content, zip.getInputStream(zip.getEntry("random.bin")).readAllBytes());
        }
    }

    @Test
    void sequentialReadWithZipInputStream() throws IOException {
        Path first = pack("a.txt", "alpha".getBytes(StandardCharsets.UTF_8), CompressionMethod.DEFLATED);
        Path second = pack("b.txt", "beta".getBytes(StandardCharsets.UTF_8), CompressionMethod.DEFLATED);

        try (ZipInputStream in = new ZipInputStream(
                Files.newInputStream(assemble(List.of(first, second), AssembleOptions.defaults())))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals("a.txt", entry.getName());
            assertEquals("alpha", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            entry = in.getNextEntry();
            assertEquals("b.txt", entry.getName());
            assertEquals("beta", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void utf8NameRoundTrip() throws IOException {
        Path key = pack("问候.txt", "你好".getBytes(StandardCharsets.UTF_8), CompressionMethod.DEFLATED);

        try (ZipFile zip = new ZipFile(assemble(List.of(key), AssembleOptions.defaults()).toFile())) {
            assertNotNull(zip.getEntry("问候.txt"));
        }
    }

    @Test
    void unixModeReachesCentralDirectory() throws IOException {
        Path input = temp.resolve("script.sh");
        Files.writeString(input, "#!/bin/sh\n");
        int mode = UnixMode.read(input);
        Path prefix = temp.resolve("store/script.sh");
        Files.createDirectories(prefix.getParent());
        Packer.pack(input, prefix, PackOptions.defaults(null));

        CentralDirectoryFileHeader parsed = HeaderParser.parseCentralDirectoryFileHeader(
                Files.readAllBytes(StoredEntryFiles.centralHeader(prefix)));
        assertEquals(UnixMode.externalAttributes(mode), parsed.externalAttributes());
    }

    @Test
    void estimateSizeMatchesAssembly() throws IOException {
        Path first = pack("one.txt", "one".getBytes(StandardCharsets.UTF_8), CompressionMethod.DEFLATED);
        Path second = pack("two.txt", "two".getBytes(StandardCharsets.UTF_8), CompressionMethod.STORED);
        List<Path> keys = List.of(first, second);

        Path zip = assemble(keys, AssembleOptions.defaults());
        assertEquals(Files.size(zip), Assembler.estimateSize(keys, AssembleOptions.defaults()));
    }

    @Test
    void forcedZip64IsReadableAndEmitsZip64Records() throws IOException {
        Path key = pack("small.txt", "small".getBytes(StandardCharsets.UTF_8), CompressionMethod.DEFLATED);
        Path zip = assemble(List.of(key), new AssembleOptions(AssembleOptions.Zip64Mode.ALWAYS, ""));

        byte[] bytes = Files.readAllBytes(zip);
        assertTrue(containsSignature(bytes, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE));
        assertTrue(containsSignature(bytes, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE));

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertArrayEquals("small".getBytes(StandardCharsets.UTF_8),
                    zf.getInputStream(zf.getEntry("small.txt")).readAllBytes());
        }
    }

    @Test
    void payloadSizeMismatchIsRejected() throws IOException {
        Path key = pack("m.txt", "abc".getBytes(StandardCharsets.UTF_8), CompressionMethod.STORED);
        Files.write(StoredEntryFiles.data(key), new byte[]{1, 2, 3, 4});

        IOException error = assertThrows(IOException.class,
                () -> assemble(List.of(key), AssembleOptions.defaults()));
        assertTrue(error.getMessage().contains("payload size mismatch"));
    }

    private static boolean containsSignature(byte[] haystack, long signature) {
        byte[] needle = LittleEndian.bytesUInt32(signature);
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
