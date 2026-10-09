package com.geno1024.ai.freezip

import java.io.OutputStream

/**
 * Little-endian (Intel byte order) helpers.
 *
 * APPNOTE.TXT §4.4.1.1: "All fields unless otherwise noted are unsigned and
 * stored in Intel low-byte:high-byte, low-word:high-word order." Every integer
 * in a ZIP record is therefore little-endian, and this object is the single
 * place that encodes/decodes them.
 */
object LittleEndian {

    fun writeUInt16(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    fun writeUInt32(out: OutputStream, value: Long) {
        out.write((value and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 24) and 0xFF).toInt())
    }

    fun writeUInt64(out: OutputStream, value: Long) {
        writeUInt32(out, value and ZipFormat.UINT32_MAX)
        writeUInt32(out, (value ushr 32) and ZipFormat.UINT32_MAX)
    }

    fun bytesUInt16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
    )

    fun bytesUInt32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte(),
    )

    fun bytesUInt64(value: Long): ByteArray = ByteArray(8) { i ->
        ((value ushr (8 * i)) and 0xFF).toByte()
    }

    fun readUInt16(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

    fun readUInt32(buffer: ByteArray, offset: Int): Long =
        (buffer[offset].toLong() and 0xFF) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 8) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 3].toLong() and 0xFF) shl 24)

    fun readUInt64(buffer: ByteArray, offset: Int): Long =
        readUInt32(buffer, offset) or (readUInt32(buffer, offset + 4) shl 32)
}
