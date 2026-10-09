package com.geno1024.ai.freezip

import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads, mutates and stores one entry's `.lfh` / `.cdfh` headers in place.
 *
 * Editing never touches `<key>.data`: every [EntryOperation] only changes
 * metadata, so the sizes, CRC and compressed payload stay valid. The central
 * header is written back as a template with local header offset 0; assembly fills
 * in the real offset later.
 */
object EntryEditor {

    /** Loads the metadata of an already packed entry. */
    fun load(key: Path): StoredEntry {
        val local = LocalFileHeader.parse(Files.readAllBytes(StoredEntryFiles.localHeader(key)))
        val central = CentralDirectoryFileHeader.parse(Files.readAllBytes(StoredEntryFiles.centralHeader(key)))
        return StoredEntry.of(local, central)
    }

    /** Applies [operations] in order and persists the rewritten headers. */
    fun edit(key: Path, operations: List<EntryOperation>): StoredEntry {
        var entry = load(key)
        for (operation in operations) {
            entry = operation.apply(entry)
        }
        save(key, entry)
        return entry
    }

    /** Writes the metadata back to `<key>.lfh` and `<key>.cdfh`. */
    fun save(key: Path, entry: StoredEntry) {
        Files.write(StoredEntryFiles.localHeader(key), LocalFileHeader.of(entry).serialize())
        Files.write(StoredEntryFiles.centralHeader(key), CentralDirectoryFileHeader.of(entry, 0).serialize())
    }
}
