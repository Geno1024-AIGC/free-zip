package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The end of central directory record. APPNOTE.TXT §4.3.16.
 *
 * <p>Fixed 22-byte record (plus an optional archive comment) that closes every
 * archive. It duplicates the central directory size and offset, each as a 32-bit
 * field; when the real value overflows, it saturates to {@code 0xFFFFFFFF} and a
 * Zip64 record (§4.3.14) carries the truth.
 */
public record EndOfCentralDirectoryRecord(
        int numberOfThisDisk,
        int diskWithCentralDirectoryStart,
        int entriesOnThisDisk,
        int totalEntries,
        long centralDirectorySize,
        long centralDirectoryOffset,
        String comment
) {

    /** Serializes the record, saturating overflowing 16/32-bit fields. */
    public byte[] serialize() {
        byte[] commentBytes = comment == null ? new byte[0] : comment.getBytes(StandardCharsets.UTF_8);
        int entries = entriesOnThisDisk >= ZipFormat.UINT16_MAX ? ZipFormat.UINT16_MAX : entriesOnThisDisk;
        int total = totalEntries >= ZipFormat.UINT16_MAX ? ZipFormat.UINT16_MAX : totalEntries;
        long size = centralDirectorySize >= ZipFormat.UINT32_MAX ? ZipFormat.UINT32_MAX : centralDirectorySize;
        long offset = centralDirectoryOffset >= ZipFormat.UINT32_MAX
                ? ZipFormat.UINT32_MAX : centralDirectoryOffset;

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE + commentBytes.length);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE);
            LittleEndian.writeUInt16(out, numberOfThisDisk);
            LittleEndian.writeUInt16(out, diskWithCentralDirectoryStart);
            LittleEndian.writeUInt16(out, entries);
            LittleEndian.writeUInt16(out, total);
            LittleEndian.writeUInt32(out, size);
            LittleEndian.writeUInt32(out, offset);
            LittleEndian.writeUInt16(out, commentBytes.length);
            out.writeBytes(commentBytes);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }
}
