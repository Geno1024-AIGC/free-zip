package com.geno1024.ai.freezip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads, mutates and stores one entry's `.lfh` / `.cdfh` headers in place.
 *
 * <p>Editing never touches `<key>.data`: every {@link EntryOperation} only changes
 * metadata, so the sizes, CRC and compressed payload stay valid. The central
 * header is written back as a template with local header offset 0; assembly fills
 * in the real offset later.
 */
public final class EntryEditor {

    private EntryEditor() {
    }

    /** Loads the metadata of an already packed entry. */
    public static StoredEntry load(Path key) throws IOException {
        LocalFileHeader local = LocalFileHeader.parse(
                Files.readAllBytes(StoredEntryFiles.localHeader(key)));
        CentralDirectoryFileHeader central = CentralDirectoryFileHeader.parse(
                Files.readAllBytes(StoredEntryFiles.centralHeader(key)));
        return StoredEntry.of(local, central);
    }

    /** Applies {@code operations} in order and persists the rewritten headers. */
    public static StoredEntry edit(Path key, List<EntryOperation> operations) throws IOException {
        StoredEntry entry = load(key);
        for (EntryOperation operation : operations) {
            entry = operation.apply(entry);
        }
        save(key, entry);
        return entry;
    }

    /** Writes the metadata back to `<key>.lfh` and `<key>.cdfh`. */
    public static void save(Path key, StoredEntry entry) throws IOException {
        Files.write(StoredEntryFiles.localHeader(key), LocalFileHeader.of(entry).serialize());
        Files.write(StoredEntryFiles.centralHeader(key), CentralDirectoryFileHeader.of(entry, 0).serialize());
    }
}
