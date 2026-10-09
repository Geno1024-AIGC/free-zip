package com.geno1024.ai.freezip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The Zip64 extended information extra field (header id {@code 0x0001}).
 *
 * <p>APPNOTE.TXT §4.5.3. It is emitted whenever a 32-bit size or offset field is
 * saturates to {@code 0xFFFFFFFF}. The field order is fixed (original
 * uncompressed size, compressed size, local header offset, disk start number)
 * and each value only appears when its 32-bit counterpart is saturated.
 */
public final class Zip64ExtendedInformationExtraField {

    /** Extra field header id. APPNOTE.TXT §4.5.3. */
    public static final int HEADER_ID = 0x0001;

    private Zip64ExtendedInformationExtraField() {
    }

    /**
     * Serializes the field.
     *
     * <p>Note the §4.5.3 requirement that when the field appears in a local file
     * header it must carry <em>both</em> the uncompressed and compressed sizes.
     */
    public static byte[] serialize(long uncompressedSize, long compressedSize, long localHeaderOffset,
                                   int diskStart, boolean needUncompressed, boolean needCompressed,
                                   boolean needOffset, boolean needDisk) {
        if (!needUncompressed && !needCompressed && !needOffset && !needDisk) {
            return new byte[0];
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(28);
        try {
            if (needUncompressed) {
                LittleEndian.writeUInt64(body, uncompressedSize);
            }
            if (needCompressed) {
                LittleEndian.writeUInt64(body, compressedSize);
            }
            if (needOffset) {
                LittleEndian.writeUInt64(body, localHeaderOffset);
            }
            if (needDisk) {
                LittleEndian.writeUInt32(body, diskStart);
            }
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + body.size());
        try {
            LittleEndian.writeUInt16(out, HEADER_ID);
            LittleEndian.writeUInt16(out, body.size());
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }
}
