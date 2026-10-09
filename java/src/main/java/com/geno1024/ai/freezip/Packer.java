package com.geno1024.ai.freezip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Turns one file into a stored entry: {@code <key>.data}, {@code <key>.lfh} and
 * {@code <key>.cdfh} (see {@link StoredEntryFiles}).
 *
 * <p>This is the "file → {@code file.data}" half of the pipeline. The payload is
 * compressed once, here, and later only copied by the {@link Assembler}; the
 * headers are emitted from the same {@link StoredEntry}, so the local and
 * central copies can never disagree.
 */
public final class Packer {

    private static final int BUFFER_SIZE = 1 << 16;

    private Packer() {
    }

    /**
     * Packs {@code input} into the three files derived from {@code outputPrefix}.
     *
     * @return the metadata that was written into the headers
     */
    public static StoredEntry pack(Path input, Path outputPrefix, PackOptions options) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IOException("not a regular file: " + input);
        }
        String name = options.entryName() == null || options.entryName().isEmpty()
                ? input.getFileName().toString()
                : options.entryName();

        CRC32 crc = new CRC32();
        PayloadSizes sizes = writePayload(input, StoredEntryFiles.data(outputPrefix), options, crc);

        int mode = UnixMode.read(input);
        int hostSystem = mode >= 0 ? ZipFormat.HOST_UNIX : ZipFormat.HOST_DOS;
        int externalAttributes = mode >= 0 ? UnixMode.externalAttributes(mode) : 0;

        LocalDateTime modified = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(input).toInstant(), ZoneId.systemDefault());

        StoredEntry entry = new StoredEntry(
                name,
                options.method(),
                crc.getValue(),
                sizes.compressed(),
                sizes.uncompressed(),
                MsDosDateTime.of(modified),
                hostSystem,
                externalAttributes,
                options.comment());

        Files.write(StoredEntryFiles.localHeader(outputPrefix), LocalFileHeader.of(entry).serialize());
        Files.write(StoredEntryFiles.centralHeader(outputPrefix),
                CentralDirectoryFileHeader.of(entry, 0).serialize());
        return entry;
    }

    private static PayloadSizes writePayload(Path input, Path dataPath, PackOptions options, CRC32 crc)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long uncompressed = 0;
        long compressed;
        try (InputStream in = Files.newInputStream(input);
             OutputStream rawOut = Files.newOutputStream(dataPath)) {
            if (options.method() == CompressionMethod.STORED) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    crc.update(buffer, 0, read);
                    rawOut.write(buffer, 0, read);
                    uncompressed += read;
                }
                compressed = uncompressed;
                rawOut.flush();
            } else {
                // §4.3.8: the payload is a raw DEFLATE stream, without a zlib header.
                Deflater deflater = new Deflater(options.compressionLevel(), true);
                CountingOutputStream counting = new CountingOutputStream(rawOut);
                try (DeflaterOutputStream deflated =
                             new DeflaterOutputStream(counting, deflater, BUFFER_SIZE, false)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        crc.update(buffer, 0, read);
                        deflated.write(buffer, 0, read);
                        uncompressed += read;
                    }
                    deflated.finish();
                    compressed = counting.count();
                } finally {
                    deflater.end();
                }
            }
        }
        return new PayloadSizes(uncompressed, compressed);
    }

    private record PayloadSizes(long uncompressed, long compressed) {
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            count += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
    }
}
