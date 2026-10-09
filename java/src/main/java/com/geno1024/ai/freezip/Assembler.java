package com.geno1024.ai.freezip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles stored entries into a complete ZIP archive, in a single pass.
 *
 * <p>Given a list of entry keys (see {@link StoredEntryFiles}), this copies each
 * entry's local file header (§4.3.7) followed by its payload (§4.3.8) verbatim,
 * then appends the central directory (§4.3.12) and end records (§4.3.14/15/16).
 *
 * <p>Because the central directory is written last, the local header offsets are
 * known by the time they are needed — that is what makes the pre-stored header
 * approach work. Nothing is recompressed.
 */
public final class Assembler {

    private static final int BUFFER_SIZE = 1 << 16;

    private Assembler() {
    }

    /** Assembles {@code entryKeys} into {@code out}. */
    public static void assemble(List<Path> entryKeys, OutputStream out, AssembleOptions options)
            throws IOException {
        List<Path> keys = entryKeys;
        long offset = 0;
        long count = 0;
        long centralDirectorySize = 0;
        List<byte[]> centralEntries = new ArrayList<>(keys.size());

        for (Path key : keys) {
            byte[] localBytes = Files.readAllBytes(StoredEntryFiles.localHeader(key));
            byte[] centralBytes = Files.readAllBytes(StoredEntryFiles.centralHeader(key));
            LocalFileHeader local = HeaderParser.parseLocalFileHeader(localBytes);
            CentralDirectoryFileHeader central = HeaderParser.parseCentralDirectoryFileHeader(centralBytes);
            StoredEntry entry = HeaderParser.toStoredEntry(local, central);

            Path dataPath = StoredEntryFiles.data(key);
            long dataSize = Files.size(dataPath);
            if (dataSize != entry.compressedSize()) {
                throw new IOException("payload size mismatch for " + entry.name()
                        + ": header says " + entry.compressedSize() + " but " + dataPath + " has " + dataSize);
            }

            long localHeaderOffset = offset;
            out.write(localBytes);                                  // §4.3.7
            copy(dataPath, out);                                    // §4.3.8
            offset += localBytes.length + dataSize;

            byte[] centralEntry = HeaderCodec.centralDirectoryFileHeader(entry, localHeaderOffset);
            centralEntries.add(centralEntry);
            centralDirectorySize += centralEntry.length;
            count++;
        }

        long centralDirectoryOffset = offset;
        for (byte[] centralEntry : centralEntries) {
            out.write(centralEntry);                                // §4.3.12
        }
        writeEndRecords(out, count, centralDirectorySize, centralDirectoryOffset, options);
        out.flush();
    }

    /**
     * Computes the exact byte length {@link #assemble} would produce, without
     * reading any payload. Useful for an HTTP {@code Content-Length}.
     */
    public static long estimateSize(List<Path> entryKeys, AssembleOptions options) throws IOException {
        long offset = 0;
        long count = 0;
        long centralDirectorySize = 0;

        for (Path key : entryKeys) {
            byte[] localBytes = Files.readAllBytes(StoredEntryFiles.localHeader(key));
            byte[] centralBytes = Files.readAllBytes(StoredEntryFiles.centralHeader(key));
            LocalFileHeader local = HeaderParser.parseLocalFileHeader(localBytes);
            CentralDirectoryFileHeader central = HeaderParser.parseCentralDirectoryFileHeader(centralBytes);
            StoredEntry entry = HeaderParser.toStoredEntry(local, central);

            long dataSize = Files.size(StoredEntryFiles.data(key));
            centralDirectorySize += HeaderCodec.centralDirectoryFileHeader(entry, offset).length;
            offset += localBytes.length + dataSize;
            count++;
        }

        long centralDirectoryOffset = offset;
        long total = offset + centralDirectorySize;
        if (zip64Required(count, centralDirectorySize, centralDirectoryOffset, options)) {
            total += ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE
                    + ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE;
        }
        total += ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE + commentBytes(options).length;
        return total;
    }

    private static void writeEndRecords(OutputStream out, long count, long centralDirectorySize,
                                        long centralDirectoryOffset, AssembleOptions options)
            throws IOException {
        if (zip64Required(count, centralDirectorySize, centralDirectoryOffset, options)) {
            long zip64EndOffset = centralDirectoryOffset + centralDirectorySize;
            writeZip64EndOfCentralDirectoryRecord(out, count, centralDirectorySize, centralDirectoryOffset);
            writeZip64EndOfCentralDirectoryLocator(out, zip64EndOffset);
        }
        writeEndOfCentralDirectoryRecord(out, count, centralDirectorySize, centralDirectoryOffset, options);
    }

    private static boolean zip64Required(long count, long centralDirectorySize, long centralDirectoryOffset,
                                         AssembleOptions options) {
        return switch (options.zip64()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> count >= ZipFormat.UINT16_MAX
                    || centralDirectorySize >= ZipFormat.UINT32_MAX
                    || centralDirectoryOffset >= ZipFormat.UINT32_MAX;
        };
    }

    /** Zip64 end of central directory record. APPNOTE.TXT §4.3.14. */
    private static void writeZip64EndOfCentralDirectoryRecord(OutputStream out, long count,
                                                              long centralDirectorySize,
                                                              long centralDirectoryOffset) throws IOException {
        LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE);
        // §4.3.14.1: size excludes the leading 12 bytes.
        LittleEndian.writeUInt64(out,
                ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE - 12);
        LittleEndian.writeUInt16(out, (ZipFormat.HOST_UNIX << 8) | ZipFormat.VERSION_NEEDED_ZIP64);
        LittleEndian.writeUInt16(out, ZipFormat.VERSION_NEEDED_ZIP64);
        LittleEndian.writeUInt32(out, 0);                 // number of this disk
        LittleEndian.writeUInt32(out, 0);                 // disk with start of central directory
        LittleEndian.writeUInt64(out, count);             // entries on this disk
        LittleEndian.writeUInt64(out, count);             // total entries
        LittleEndian.writeUInt64(out, centralDirectorySize);
        LittleEndian.writeUInt64(out, centralDirectoryOffset);
    }

    /** Zip64 end of central directory locator. APPNOTE.TXT §4.3.15. */
    private static void writeZip64EndOfCentralDirectoryLocator(OutputStream out, long zip64EndOffset)
            throws IOException {
        LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE);
        LittleEndian.writeUInt32(out, 0);                 // disk with the Zip64 EOCD record
        LittleEndian.writeUInt64(out, zip64EndOffset);
        LittleEndian.writeUInt32(out, 1);                 // total number of disks
    }

    /** End of central directory record. APPNOTE.TXT §4.3.16. */
    private static void writeEndOfCentralDirectoryRecord(OutputStream out, long count,
                                                         long centralDirectorySize,
                                                         long centralDirectoryOffset,
                                                         AssembleOptions options) throws IOException {
        boolean saturate = options.zip64() == AssembleOptions.Zip64Mode.ALWAYS;
        int entries = saturate || count >= ZipFormat.UINT16_MAX ? ZipFormat.UINT16_MAX : (int) count;
        long size = saturate || centralDirectorySize >= ZipFormat.UINT32_MAX
                ? ZipFormat.UINT32_MAX : centralDirectorySize;
        long offset = saturate || centralDirectoryOffset >= ZipFormat.UINT32_MAX
                ? ZipFormat.UINT32_MAX : centralDirectoryOffset;
        byte[] comment = commentBytes(options);

        LittleEndian.writeUInt32(out, ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE);
        LittleEndian.writeUInt16(out, 0);                 // number of this disk
        LittleEndian.writeUInt16(out, 0);                 // disk with start of central directory
        LittleEndian.writeUInt16(out, entries);           // entries on this disk
        LittleEndian.writeUInt16(out, entries);           // total entries
        LittleEndian.writeUInt32(out, size);
        LittleEndian.writeUInt32(out, offset);
        LittleEndian.writeUInt16(out, comment.length);
        out.write(comment);
    }

    private static byte[] commentBytes(AssembleOptions options) {
        return options.comment().getBytes(StandardCharsets.UTF_8);
    }

    private static void copy(Path file, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
