package com.geno1024.ai.freezip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Validates a stored entry's three-file triad.
 *
 * <p>Verification is the mirror image of {@link Packer}: it streams the payload
 * and checks that the file size and CRC-32 agree with the headers, so a corrupt
 * or truncated `<key>.data` is caught before it is served or assembled.
 *
 * <p>The CRC-32 field covers the <em>uncompressed</em> content (APPNOTE.TXT
 * §4.4.7), so deflated payloads are inflated on the fly before checksumming.
 */
public final class Verifier {

    private static final int BUFFER_SIZE = 1 << 16;

    private Verifier() {
    }

    /**
     * Verifies that `<key>.lfh` and `<key>.cdfh` describe `<key>.data` exactly.
     *
     * @return the verified entry metadata
     * @throws IOException when a file is missing, or size or CRC does not match
     */
    public static StoredEntry verify(Path key) throws IOException {
        StoredEntry entry = EntryEditor.load(key);

        Path dataPath = StoredEntryFiles.data(key);
        long size = Files.size(dataPath);
        if (size != entry.compressedSize()) {
            throw new IOException("size mismatch for " + entry.name()
                    + ": header says " + entry.compressedSize() + " but " + dataPath + " has " + size);
        }

        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        InputStream source = Files.newInputStream(dataPath);
        if (entry.method() == CompressionMethod.DEFLATED) {
            // Packer emits a raw DEFLATE stream without a zlib header (§4.3.8).
            source = new InflaterInputStream(source, new Inflater(true));
        }
        try (InputStream in = source) {
            long expanded = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
                expanded += read;
            }
            if (expanded != entry.uncompressedSize()) {
                throw new IOException("uncompressed size mismatch for " + entry.name()
                        + ": header says " + entry.uncompressedSize()
                        + " but inflating yields " + expanded);
            }
        }
        if (crc.getValue() != entry.crc32()) {
            throw new IOException("CRC-32 mismatch for " + entry.name()
                    + ": header says " + Long.toHexString(entry.crc32())
                    + " but payload hashes to " + Long.toHexString(crc.getValue()));
        }
        return entry;
    }
}