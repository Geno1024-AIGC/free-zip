package com.geno1024.ai.freezip

import java.util.zip.Deflater

/**
 * Options controlling how a single file is packed into a stored entry.
 *
 * Only the fields that must be known while the payload is compressed live here;
 * pure metadata such as the entry comment is a separate [EntryOperation] applied
 * afterwards with [EntryEditor].
 *
 * @param entryName name stored inside the archive; defaults to the source file
 *   name when `null`
 * @param method compression method; [CompressionMethod.DEFLATED] is recommended
 *   except for already-compressed payloads
 * @param compressionLevel DEFLATE level, see [Deflater]
 */
data class PackOptions(
    val entryName: String?,
    val method: CompressionMethod,
    val compressionLevel: Int,
) {
    companion object {
        fun defaults(entryName: String?): PackOptions =
            PackOptions(entryName, CompressionMethod.DEFLATED, Deflater.DEFAULT_COMPRESSION)
    }

    fun withEntryName(value: String?): PackOptions = copy(entryName = value)

    fun withMethod(value: CompressionMethod): PackOptions = copy(method = value)

    fun withCompressionLevel(value: Int): PackOptions = copy(compressionLevel = value)
}