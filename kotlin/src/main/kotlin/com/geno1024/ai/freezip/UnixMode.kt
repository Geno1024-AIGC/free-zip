package com.geno1024.ai.freezip

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

/**
 * Maps a file's POSIX mode to the ZIP "external file attributes" field.
 *
 * APPNOTE.TXT §4.4.15: when the "version made by" host is UNIX (3), the upper
 * 16 bits of the external attributes hold the `st_mode` value, so storing a
 * file's mode is what lets Unix extractors restore the executable bit.
 */
object UnixMode {

    // Kotlin has no octal literals, so the POSIX constants are given in hex.
    const val TYPE_REGULAR = 0x8000   // octal 0100000
    const val TYPE_DIRECTORY = 0x4000 // octal 0040000

    private const val OWNER_READ = 0x100
    private const val OWNER_WRITE = 0x80
    private const val OWNER_EXECUTE = 0x40
    private const val GROUP_READ = 0x20
    private const val GROUP_WRITE = 0x10
    private const val GROUP_EXECUTE = 0x8
    private const val OTHERS_READ = 0x4
    private const val OTHERS_WRITE = 0x2
    private const val OTHERS_EXECUTE = 0x1

    /** Reads the mode of a file, or returns `-1` when POSIX is unavailable. */
    fun read(file: Path): Int =
        try {
            val attributes = Files.readAttributes(file, PosixFileAttributes::class.java)
            var mode = if (attributes.isDirectory) TYPE_DIRECTORY else TYPE_REGULAR
            for (permission in attributes.permissions()) {
                mode = mode or bit(permission)
            }
            mode
        } catch (_: UnsupportedOperationException) {
            -1
        } catch (_: IOException) {
            -1
        }

    /** Packs a mode into the 32-bit external attributes field (upper 16 bits). */
    fun externalAttributes(mode: Int): Int = mode shl 16

    private fun bit(permission: PosixFilePermission): Int = when (permission) {
        PosixFilePermission.OWNER_READ -> OWNER_READ
        PosixFilePermission.OWNER_WRITE -> OWNER_WRITE
        PosixFilePermission.OWNER_EXECUTE -> OWNER_EXECUTE
        PosixFilePermission.GROUP_READ -> GROUP_READ
        PosixFilePermission.GROUP_WRITE -> GROUP_WRITE
        PosixFilePermission.GROUP_EXECUTE -> GROUP_EXECUTE
        PosixFilePermission.OTHERS_READ -> OTHERS_READ
        PosixFilePermission.OTHERS_WRITE -> OTHERS_WRITE
        PosixFilePermission.OTHERS_EXECUTE -> OTHERS_EXECUTE
    }
}
