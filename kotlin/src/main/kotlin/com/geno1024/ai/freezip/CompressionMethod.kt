package com.geno1024.ai.freezip

/**
 * Compression methods supported by this library.
 *
 * Only the two methods that every mainstream extractor understands are offered:
 * `stored` and `deflated`. See APPNOTE.TXT §4.4.5 (compression method) and
 * §4.4.3 (version needed to extract).
 */
enum class CompressionMethod(val id: Int, val versionNeeded: Int) {

    /** Method 0: no compression. APPNOTE.TXT §4.4.5. */
    STORED(0, ZipFormat.VERSION_NEEDED_STORED),

    /** Method 8: raw DEFLATE. APPNOTE.TXT §4.4.5. */
    DEFLATED(8, ZipFormat.VERSION_NEEDED_DEFLATED);

    companion object {
        /** Resolves a compression method id, throwing when it is unsupported. */
        fun fromId(id: Int): CompressionMethod =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unsupported compression method: $id")
    }
}
