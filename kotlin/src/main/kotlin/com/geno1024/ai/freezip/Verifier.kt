package com.geno1024.ai.freezip

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Validates a stored entry's three-file triad.
 *
 * Verification is the mirror image of [Packer]: it streams the payload and checks
 * that the file size and CRC-32 agree with the headers, so a corrupt or truncated
 * `<key>.data` is caught before it is served or assembled.
 *
 * The CRC-32 field covers the *uncompressed* content (APPNOTE.TXT §4.4.7), so
 * deflated payloads are inflated on the fly before checksumming.
 */
object Verifier {

    private const val BUFFER_SIZE = 1 shl 16

    /**
     * Verifies that `<key>.lfh` and `<key>.cdfh` describe `<key>.data` exactly.
     *
     * @return the verified entry metadata
     * @throws IOException when a file is missing, or size or CRC does not match
     */
    fun verify(key: Path): StoredEntry {
        val entry = EntryEditor.load(key)

        val dataPath = StoredEntryFiles.data(key)
        val size = Files.size(dataPath)
        if (size != entry.compressedSize) {
            throw IOException(
                "size mismatch for ${entry.name}: header says ${entry.compressedSize} " +
                    "but $dataPath has $size",
            )
        }

        val crc = CRC32()
        val buffer = ByteArray(BUFFER_SIZE)
        var source: InputStream = Files.newInputStream(dataPath)
        if (entry.method == CompressionMethod.DEFLATED) {
            // Packer emits a raw DEFLATE stream without a zlib header (§4.3.8).
            source = InflaterInputStream(source, Inflater(true))
        }
        source.use {
            var expanded = 0L
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                crc.update(buffer, 0, read)
                expanded += read
            }
            if (expanded != entry.uncompressedSize) {
                throw IOException(
                    "uncompressed size mismatch for ${entry.name}: header says ${entry.uncompressedSize} " +
                        "but inflating yields $expanded",
                )
            }
        }
        if (crc.value != entry.crc32) {
            throw IOException(
                "CRC-32 mismatch for ${entry.name}: header says ${entry.crc32.toString(16)} " +
                    "but payload hashes to ${crc.value.toString(16)}",
            )
        }
        return entry
    }
}