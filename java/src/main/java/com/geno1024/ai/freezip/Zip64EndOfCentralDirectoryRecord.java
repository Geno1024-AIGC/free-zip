package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The Zip64 end of central directory record. APPNOTE.TXT §4.3.14.
 *
 * <p>Present only when Zip64 is in use; it repeats the end-of-central-directory
 * totals as 64-bit fields. The size field (§4.3.14.1) counts everything after
 * itself, i.e. the fixed record length minus the signature and the size field.
 */
public record Zip64EndOfCentralDirectoryRecord(
        int versionMadeBy,
        int versionNeeded,
        long numberOfThisDisk,
        long diskWithCentralDirectoryStart,
        long entriesOnThisDisk,
        long totalEntries,
        long centralDirectorySize,
        long centralDirectoryOffset
) {

    /** Serializes the fixed 56-byte record. */
    public byte[] serialize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE);
        try {
            LittleEndian.writeUInt32(out, ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE);
            // §4.3.14.1: size excludes the leading 12 bytes.
            LittleEndian.writeUInt64(out,
                    ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE - 12);
            LittleEndian.writeUInt16(out, versionMadeBy);
            LittleEndian.writeUInt16(out, versionNeeded);
            LittleEndian.writeUInt32(out, numberOfThisDisk);
            LittleEndian.writeUInt32(out, diskWithCentralDirectoryStart);
            LittleEndian.writeUInt64(out, entriesOnThisDisk);
            LittleEndian.writeUInt64(out, totalEntries);
            LittleEndian.writeUInt64(out, centralDirectorySize);
            LittleEndian.writeUInt64(out, centralDirectoryOffset);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return out.toByteArray();
    }
}
