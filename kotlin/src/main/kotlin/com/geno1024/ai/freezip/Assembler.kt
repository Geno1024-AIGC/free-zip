package com.geno1024.ai.freezip

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashSet

/**
 * Assembles stored entries into a complete ZIP archive, in a single pass.
 *
 * Given a list of entry keys (see [StoredEntryFiles]), this copies each entry's
 * local file header (§4.3.7) followed by its payload (§4.3.8) verbatim, then
 * appends the central directory (§4.3.12) and end records (§4.3.14/15/16).
 *
 * Because the central directory is written last, the local header offsets are
 * known by the time they are needed — that is what makes the pre-stored header
 * approach work. Nothing is recompressed.
 */
object Assembler {

    private const val BUFFER_SIZE = 1 shl 16

    /** Assembles [entryKeys] into [out]. */
    fun assemble(entryKeys: List<Path>, out: OutputStream, options: AssembleOptions) {
        var offset = 0L
        var count = 0L
        var centralDirectorySize = 0L
        val centralEntries = ArrayList<ByteArray>(entryKeys.size)
        val seenNames = HashSet<String>(entryKeys.size)

        for (key in entryKeys) {
            val localBytes = Files.readAllBytes(StoredEntryFiles.localHeader(key))
            val centralBytes = Files.readAllBytes(StoredEntryFiles.centralHeader(key))
            val local = LocalFileHeader.parse(localBytes)
            val central = CentralDirectoryFileHeader.parse(centralBytes)
            val entry = StoredEntry.of(local, central)
            if (!seenNames.add(entry.name)) {
                throw IOException("duplicate entry name: ${entry.name}")
            }

            val dataPath = StoredEntryFiles.data(key)
            val dataSize = Files.size(dataPath)
            if (dataSize != entry.compressedSize) {
                throw IOException(
                    "payload size mismatch for ${entry.name}: header says " +
                        "${entry.compressedSize} but $dataPath has $dataSize",
                )
            }

            val localHeaderOffset = offset
            out.write(localBytes)                                  // §4.3.7
            copy(dataPath, out)                                    // §4.3.8
            offset += localBytes.size + dataSize

            val centralEntry = CentralDirectoryFileHeader.of(entry, localHeaderOffset).serialize()
            centralEntries.add(centralEntry)
            centralDirectorySize += centralEntry.size
            count++
        }

        val centralDirectoryOffset = offset
        for (centralEntry in centralEntries) {
            out.write(centralEntry)                                // §4.3.12
        }
        writeEndRecords(out, count, centralDirectorySize, centralDirectoryOffset, options)
        out.flush()
    }

    /**
     * Computes the exact byte length [assemble] would produce, without reading any
     * payload. Useful for an HTTP `Content-Length`.
     */
    fun estimateSize(entryKeys: List<Path>, options: AssembleOptions): Long {
        var offset = 0L
        var count = 0L
        var centralDirectorySize = 0L
        val seenNames = HashSet<String>(entryKeys.size)

        for (key in entryKeys) {
            val localBytes = Files.readAllBytes(StoredEntryFiles.localHeader(key))
            val centralBytes = Files.readAllBytes(StoredEntryFiles.centralHeader(key))
            val local = LocalFileHeader.parse(localBytes)
            val central = CentralDirectoryFileHeader.parse(centralBytes)
            val entry = StoredEntry.of(local, central)
            if (!seenNames.add(entry.name)) {
                throw IOException("duplicate entry name: ${entry.name}")
            }

            val dataSize = Files.size(StoredEntryFiles.data(key))
            centralDirectorySize += CentralDirectoryFileHeader.of(entry, offset).serialize().size
            offset += localBytes.size + dataSize
            count++
        }

        val centralDirectoryOffset = offset
        var total = offset + centralDirectorySize
        if (zip64Required(count, centralDirectorySize, centralDirectoryOffset, options)) {
            total += ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE +
                ZipFormat.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE
        }
        total += ZipFormat.END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE + commentBytes(options).size
        return total
    }

    private fun writeEndRecords(
        out: OutputStream,
        count: Long,
        centralDirectorySize: Long,
        centralDirectoryOffset: Long,
        options: AssembleOptions,
    ) {
        if (zip64Required(count, centralDirectorySize, centralDirectoryOffset, options)) {
            val zip64EndOffset = centralDirectoryOffset + centralDirectorySize
            out.write(
                Zip64EndOfCentralDirectoryRecord(                        // §4.3.14
                    (ZipFormat.HOST_UNIX shl 8) or ZipFormat.VERSION_NEEDED_ZIP64,
                    ZipFormat.VERSION_NEEDED_ZIP64,
                    0, 0, count, count,
                    centralDirectorySize, centralDirectoryOffset,
                ).serialize(),
            )
            out.write(Zip64EndOfCentralDirectoryLocator(0, zip64EndOffset, 1).serialize()) // §4.3.15
        }

        val saturate = options.zip64 == AssembleOptions.Zip64Mode.ALWAYS
        val entries = if (saturate || count >= ZipFormat.UINT16_MAX) ZipFormat.UINT16_MAX else count.toInt()
        val size = if (saturate || centralDirectorySize >= ZipFormat.UINT32_MAX) {
            ZipFormat.UINT32_MAX
        } else {
            centralDirectorySize
        }
        val offset = if (saturate || centralDirectoryOffset >= ZipFormat.UINT32_MAX) {
            ZipFormat.UINT32_MAX
        } else {
            centralDirectoryOffset
        }
        out.write(
            EndOfCentralDirectoryRecord(0, 0, entries, entries, size, offset, options.comment).serialize(),
        )                                                                 // §4.3.16
    }

    private fun zip64Required(
        count: Long,
        centralDirectorySize: Long,
        centralDirectoryOffset: Long,
        options: AssembleOptions,
    ): Boolean {
        val needed = count >= ZipFormat.UINT16_MAX ||
            centralDirectorySize >= ZipFormat.UINT32_MAX ||
            centralDirectoryOffset >= ZipFormat.UINT32_MAX
        return when (options.zip64) {
            AssembleOptions.Zip64Mode.ALWAYS -> true
            AssembleOptions.Zip64Mode.NEVER -> {
                if (needed) {
                    throw IOException(
                        "--zip64 never but the archive requires Zip64 " +
                            "(too many entries, or central directory size/offset exceeds 4 GiB)",
                    )
                }
                false
            }
            AssembleOptions.Zip64Mode.AUTO -> needed
        }
    }

    private fun commentBytes(options: AssembleOptions): ByteArray =
        options.comment.toByteArray(StandardCharsets.UTF_8)

    private fun copy(file: Path, out: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        Files.newInputStream(file).use { inputStream: InputStream ->
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
            }
        }
    }
}
