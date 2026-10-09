package com.geno1024.ai.freezip;

/**
 * Helpers for the extra field list that may follow a header.
 *
 * <p>APPNOTE.TXT §4.5.2: the extra field is a sequence of
 * {@code (header id, data size, data)} blocks. This is the single place that
 * walks that list.
 */
public final class ExtraField {

    private ExtraField() {
    }

    /**
     * Returns the body of the first extra field with the given header id, or
     * {@code null} when it is absent or malformed.
     */
    public static byte[] find(byte[] extra, int headerId) {
        int cursor = 0;
        while (cursor + 4 <= extra.length) {
            int id = LittleEndian.readUInt16(extra, cursor);
            int size = LittleEndian.readUInt16(extra, cursor + 2);
            cursor += 4;
            if (cursor + size > extra.length) {
                return null;
            }
            if (id == headerId) {
                byte[] body = new byte[size];
                System.arraycopy(extra, cursor, body, 0, size);
                return body;
            }
            cursor += size;
        }
        return null;
    }
}
