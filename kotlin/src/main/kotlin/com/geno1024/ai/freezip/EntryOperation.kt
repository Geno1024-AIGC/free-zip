package com.geno1024.ai.freezip

import java.time.LocalDateTime

/**
 * A single mutation applied to a stored entry's metadata.
 *
 * Operations are pure functions over [StoredEntry]: they never touch the
 * compressed payload, so `<key>.data` is left untouched. The fields they change
 * live on one of two levels:
 * - central-directory only — comment (§4.3.12), external attributes (§4.4.15);
 * - both headers — name (§4.3.7 / §4.3.12), modification time (§4.4.6).
 *
 * The local header offset is not a field here; it is filled in during assembly.
 */
sealed interface EntryOperation {

    /** Returns the entry with this operation applied. */
    fun apply(entry: StoredEntry): StoredEntry

    /** Replaces the entry comment (central directory only). APPNOTE.TXT §4.3.12. */
    data class SetComment(val comment: String) : EntryOperation {
        override fun apply(entry: StoredEntry): StoredEntry = entry.copy(comment = comment)
    }

    /** Renames the entry. APPNOTE.TXT §4.3.7 / §4.3.12. */
    data class Rename(val name: String) : EntryOperation {
        init {
            require(name.isNotEmpty()) { "name must not be empty" }
        }

        override fun apply(entry: StoredEntry): StoredEntry = entry.copy(name = name)
    }

    /** Sets the modification time (both headers). APPNOTE.TXT §4.4.6. */
    data class SetModificationTime(val time: LocalDateTime) : EntryOperation {
        override fun apply(entry: StoredEntry): StoredEntry = entry.copy(dosTime = MsDosDateTime.of(time))
    }

    /** Sets the POSIX mode as UNIX external attributes. APPNOTE.TXT §4.4.15. */
    data class SetUnixMode(val mode: Int) : EntryOperation {
        override fun apply(entry: StoredEntry): StoredEntry = entry.copy(
            hostSystem = ZipFormat.HOST_UNIX,
            externalAttributes = UnixMode.externalAttributes(mode),
        )
    }
}
