package com.geno1024.ai.freezip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;

/**
 * Maps a file's POSIX mode to the ZIP "external file attributes" field.
 *
 * <p>APPNOTE.TXT §4.4.15: when the "version made by" host is UNIX (3), the upper
 * 16 bits of the external attributes hold the {@code st_mode} value, so storing
 * a file's mode here is what lets Unix extractors restore the executable bit.
 */
public final class UnixMode {

    public static final int TYPE_REGULAR = 0100000;
    public static final int TYPE_DIRECTORY = 0040000;

    private UnixMode() {
    }

    /**
     * Reads the mode of a file, or returns {@code -1} when the platform does not
     * expose POSIX permissions.
     */
    public static int read(Path file) {
        try {
            PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class);
            int mode = attributes.isDirectory() ? TYPE_DIRECTORY : TYPE_REGULAR;
            for (PosixFilePermission permission : attributes.permissions()) {
                mode |= bit(permission);
            }
            return mode;
        } catch (UnsupportedOperationException | IOException notPosix) {
            return -1;
        }
    }

    /** Packs a mode into the 32-bit external attributes field (upper 16 bits). */
    public static int externalAttributes(int mode) {
        return mode << 16;
    }

    private static int bit(PosixFilePermission permission) {
        return switch (permission) {
            case OWNER_READ -> 0400;
            case OWNER_WRITE -> 0200;
            case OWNER_EXECUTE -> 0100;
            case GROUP_READ -> 0040;
            case GROUP_WRITE -> 0020;
            case GROUP_EXECUTE -> 0010;
            case OTHERS_READ -> 0004;
            case OTHERS_WRITE -> 0002;
            case OTHERS_EXECUTE -> 0001;
        };
    }
}
