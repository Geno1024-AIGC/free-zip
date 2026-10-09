package com.geno1024.ai.freezip;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Little-endian (Intel byte order) helpers.
 *
 * <p>APPNOTE.TXT §4.4.1.1: "All fields unless otherwise noted are unsigned and
 * stored in Intel low-byte:high-byte, low-word:high-word order." Every integer
 * in a ZIP record is therefore little-endian, and this class is the single place
 * that encodes/decodes them.
 */
public final class LittleEndian {

    private LittleEndian() {
    }

    public static void writeUInt16(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    public static void writeUInt32(OutputStream out, long value) throws IOException {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 24) & 0xFF));
    }

    public static void writeUInt64(OutputStream out, long value) throws IOException {
        writeUInt32(out, value & ZipFormat.UINT32_MAX);
        writeUInt32(out, (value >>> 32) & ZipFormat.UINT32_MAX);
    }

    public static byte[] bytesUInt16(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
        };
    }

    public static byte[] bytesUInt32(long value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 24) & 0xFF),
        };
    }

    public static byte[] bytesUInt64(long value) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
        return out;
    }

    public static int readUInt16(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFF) | ((buffer[offset + 1] & 0xFF) << 8);
    }

    public static long readUInt32(byte[] buffer, int offset) {
        return (buffer[offset] & 0xFFL)
                | ((buffer[offset + 1] & 0xFFL) << 8)
                | ((buffer[offset + 2] & 0xFFL) << 16)
                | ((buffer[offset + 3] & 0xFFL) << 24);
    }

    public static long readUInt64(byte[] buffer, int offset) {
        return readUInt32(buffer, offset) | (readUInt32(buffer, offset + 4) << 32);
    }
}
