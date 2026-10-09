package com.geno1024.ai.freezip

import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Turns one file into a stored entry: `<key>.data`, `<key>.lfh` and `<key>.cdfh`
 * (see [StoredEntryFiles]).
 *
 * This is the "file → `file.data`" half of the pipeline. The payload is
 * compressed once, here, and later only copied by the [Assembler]; the headers
 * are emitted from the same [StoredEntry], so the local and central copies can
 * never disagree.
 */
object Packer {

    private const val BUFFER_SIZE = 1 shl 16

    /**
     * Packs [input] into the three files derived from [outputPrefix].
     *
     * @return the metadata that was written into the headers
     */
    fun pack(input: Path, outputPrefix: Path, options: PackOptions): StoredEntry {
        if (!Files.isRegularFile(input)) {
            throw IOException("not a regular file: $input")
        }
        val name = options.entryName?.takeIf { it.isNotEmpty() } ?: input.fileName.toString()

        val crc = CRC32()
        val sizes = writePayload(input, StoredEntryFiles.data(outputPrefix), options, crc)

        val mode = UnixMode.read(input)
        val hostSystem = if (mode >= 0) ZipFormat.HOST_UNIX else ZipFormat.HOST_DOS
        val externalAttributes = if (mode >= 0) UnixMode.externalAttributes(mode) else 0

        val modified = LocalDateTime.ofInstant(
            Files.getLastModifiedTime(input).toInstant(), ZoneId.systemDefault(),
        )
        val entry = StoredEntry(
            name = name,
            method = options.method,
            crc32 = crc.value,
            compressedSize = sizes.compressed,
            uncompressedSize = sizes.uncompressed,
            dosTime = DosTime.of(modified),
            hostSystem = hostSystem,
            externalAttributes = externalAttributes,
            comment = options.comment,
        )

        Files.write(StoredEntryFiles.localHeader(outputPrefix), HeaderCodec.localFileHeader(entry))
        Files.write(
            StoredEntryFiles.centralHeader(outputPrefix),
            HeaderCodec.centralDirectoryFileHeader(entry, 0),
        )
        return entry
    }

    private fun writePayload(
        input: Path,
        dataPath: Path,
        options: PackOptions,
        crc: CRC32,
    ): PayloadSizes {
        val buffer = ByteArray(BUFFER_SIZE)
        var uncompressed = 0L
        var compressed = 0L
        Files.newInputStream(input).use { inputStream ->
            Files.newOutputStream(dataPath).use { rawOut ->
                if (options.method == CompressionMethod.STORED) {
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        crc.update(buffer, 0, read)
                        rawOut.write(buffer, 0, read)
                        uncompressed += read
                    }
                    compressed = uncompressed
                    rawOut.flush()
                } else {
                    // §4.3.8: the payload is a raw DEFLATE stream, without a zlib header.
                    val deflater = Deflater(options.compressionLevel, true)
                    val counting = CountingOutputStream(rawOut)
                    try {
                        DeflaterOutputStream(counting, deflater, BUFFER_SIZE, false).use { deflated ->
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break
                                crc.update(buffer, 0, read)
                                deflated.write(buffer, 0, read)
                                uncompressed += read
                            }
                            deflated.finish()
                            compressed = counting.count
                        }
                    } finally {
                        deflater.end()
                    }
                }
            }
        }
        return PayloadSizes(uncompressed, compressed)
    }

    private data class PayloadSizes(val uncompressed: Long, val compressed: Long)

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            delegate.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len
        }

        override fun flush() {
            delegate.flush()
        }
    }
}
