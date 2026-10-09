package com.geno1024.ai.freezip

import java.util.zip.Deflater

/**
 * Options controlling how a single file is packed into a stored entry.
 *
 * @param entryName name stored inside the archive; defaults to the source file
 *   name when `null`
 * @param method compression method; [CompressionMethod.DEFLATED] is recommended
 *   except for already-compressed payloads
 * @param compressionLevel DEFLATE level, see [Deflater]
 * @param comment optional file comment (APPNOTE.TXT §4.3.12)
 */
data class PackOptions(
    val entryName: String?,
    val method: CompressionMethod,
    val compressionLevel: Int,
    val comment: String,
) {
    companion object {
        fun defaults(entryName: String?): PackOptions =
            PackOptions(entryName, CompressionMethod.DEFLATED, Deflater.DEFAULT_COMPRESSION, "")
    }

    fun withEntryName(value: String?): PackOptions = copy(entryName = value)

    fun withMethod(value: CompressionMethod): PackOptions = copy(method = value)

    fun withCompressionLevel(value: Int): PackOptions = copy(compressionLevel = value)

    fun withComment(value: String): PackOptions = copy(comment = value)
}
