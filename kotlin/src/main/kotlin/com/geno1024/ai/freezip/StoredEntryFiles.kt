package com.geno1024.ai.freezip

import java.nio.file.Files
import java.nio.file.Path

/**
 * Naming convention for a stored entry's three files.
 *
 * Each entry is identified by a *key* path and stored as:
 * - `<key>.lfh` — local file header (APPNOTE.TXT §4.3.7)
 * - `<key>.data` — file data, i.e. the compressed payload (§4.3.8)
 * - `<key>.cdfh` — central directory file header template (§4.3.12), with the
 *   local header offset left as zero until assembly
 */
object StoredEntryFiles {

    const val LOCAL_HEADER_EXTENSION = ".lfh"
    const val DATA_EXTENSION = ".data"
    const val CENTRAL_HEADER_EXTENSION = ".cdfh"

    private val knownExtensions = listOf(
        LOCAL_HEADER_EXTENSION, DATA_EXTENSION, CENTRAL_HEADER_EXTENSION,
    )

    fun localHeader(key: Path): Path = withExtension(key, LOCAL_HEADER_EXTENSION)

    fun data(key: Path): Path = withExtension(key, DATA_EXTENSION)

    fun centralHeader(key: Path): Path = withExtension(key, CENTRAL_HEADER_EXTENSION)

    /** Strips a known stored-entry extension, yielding the entry key. */
    fun key(path: Path): Path {
        var name = path.fileName.toString()
        for (extension in knownExtensions) {
            if (name.endsWith(extension)) {
                name = name.substring(0, name.length - extension.length)
                break
            }
        }
        return path.resolveSibling(name)
    }

    fun exists(key: Path): Boolean =
        Files.isReadable(localHeader(key)) &&
            Files.isReadable(data(key)) &&
            Files.isReadable(centralHeader(key))

    private fun withExtension(key: Path, extension: String): Path {
        val stem = key(key)
        return stem.resolveSibling(stem.fileName.toString() + extension)
    }
}
