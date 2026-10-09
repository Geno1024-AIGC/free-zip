package com.geno1024.ai.freezip;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Naming convention for a stored entry's three files.
 *
 * <p>Each entry is identified by a <em>key</em> path and stored as:
 * <ul>
 *   <li>{@code <key>.lfh} — local file header (APPNOTE.TXT §4.3.7)</li>
 *   <li>{@code <key>.data} — file data, i.e. the compressed payload (§4.3.8)</li>
 *   <li>{@code <key>.cdfh} — central directory file header template (§4.3.12),
 *       with the local header offset left as zero until assembly</li>
 * </ul>
 */
public final class StoredEntryFiles {

    public static final String LOCAL_HEADER_EXTENSION = ".lfh";
    public static final String DATA_EXTENSION = ".data";
    public static final String CENTRAL_HEADER_EXTENSION = ".cdfh";

    private static final String[] KNOWN_EXTENSIONS = {
            LOCAL_HEADER_EXTENSION, DATA_EXTENSION, CENTRAL_HEADER_EXTENSION,
    };

    private StoredEntryFiles() {
    }

    public static Path localHeader(Path key) {
        return withExtension(key, LOCAL_HEADER_EXTENSION);
    }

    public static Path data(Path key) {
        return withExtension(key, DATA_EXTENSION);
    }

    public static Path centralHeader(Path key) {
        return withExtension(key, CENTRAL_HEADER_EXTENSION);
    }

    /** Strips a known stored-entry extension, yielding the entry key. */
    public static Path key(Path path) {
        String name = path.getFileName().toString();
        for (String extension : KNOWN_EXTENSIONS) {
            if (name.endsWith(extension)) {
                name = name.substring(0, name.length() - extension.length());
                break;
            }
        }
        return path.resolveSibling(name);
    }

    public static boolean exists(Path key) {
        return Files.isReadable(localHeader(key))
                && Files.isReadable(data(key))
                && Files.isReadable(centralHeader(key));
    }

    private static Path withExtension(Path key, String extension) {
        Path stem = key(key);
        return stem.resolveSibling(stem.getFileName().toString() + extension);
    }
}
