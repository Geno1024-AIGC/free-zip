package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The Zip64 end of central directory locator. APPNOTE.TXT §4.3.15.
 *
 * <p>Fixed 20-byte record placed immediately before the classic end of central
 * directory record; it points at the Zip64 record (§4.3.14) that precedes it.
 */
public record Zip64EndOfCentralDirectoryLocator(
        long diskWithZip64EndOfCentralDirectory,
        long zip64EndOfCentralDirectoryOffset,
        long totalNumberOfDisks
) {

    /** Serializes the fixed 20-byte record. */
    public byte[] serialize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE);
            LittleEndian.writeUInt32(out, diskWithZip64EndOfCentralDirectory);
            LittleEndian.writeUInt64(out, zip64EndOfCentralDirectoryOffset);
            LittleEndian.writeUInt32(out, totalNumberOfDisks);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }
}
