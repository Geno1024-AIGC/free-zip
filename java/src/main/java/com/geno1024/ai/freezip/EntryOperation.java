package com.geno1024.ai.freezip;

import java.time.LocalDateTime;

/**
 * A single mutation applied to a stored entry's metadata.
 *
 * <p>Operations are pure functions over {@link StoredEntry}: they never touch the
 * compressed payload, so `<key>.data` is left untouched. The fields they change
 * live on one of two levels:
 * <ul>
 *   <li>central-directory only — comment (§4.3.12), external attributes (§4.4.15);</li>
 *   <li>both headers — name (§4.3.7 / §4.3.12), modification time (§4.4.6).</li>
 * </ul>
 * The local header offset is not a field here; it is filled in during assembly.
 */
public sealed interface EntryOperation {

    /** Returns the entry with this operation applied. */
    StoredEntry apply(StoredEntry entry);

    /** Replaces the entry comment (central directory only). APPNOTE.TXT §4.3.12. */
    record SetComment(String comment) implements EntryOperation {
        public SetComment {
            if (comment == null) {
                throw new IllegalArgumentException("comment must not be null");
            }
        }

        @Override
        public StoredEntry apply(StoredEntry entry) {
            return new StoredEntry(entry.name(), entry.method(), entry.crc32(), entry.compressedSize(),
                    entry.uncompressedSize(), entry.dosTime(), entry.hostSystem(), entry.externalAttributes(),
                    comment);
        }
    }

    /** Renames the entry. APPNOTE.TXT §4.3.7 / §4.3.12. */
    record Rename(String name) implements EntryOperation {
        public Rename {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name must not be empty");
            }
        }

        @Override
        public StoredEntry apply(StoredEntry entry) {
            return new StoredEntry(name, entry.method(), entry.crc32(), entry.compressedSize(),
                    entry.uncompressedSize(), entry.dosTime(), entry.hostSystem(), entry.externalAttributes(),
                    entry.comment());
        }
    }

    /** Sets the modification time (both headers). APPNOTE.TXT §4.4.6. */
    record SetModificationTime(LocalDateTime time) implements EntryOperation {
        public SetModificationTime {
            if (time == null) {
                throw new IllegalArgumentException("time must not be null");
            }
        }

        @Override
        public StoredEntry apply(StoredEntry entry) {
            return new StoredEntry(entry.name(), entry.method(), entry.crc32(), entry.compressedSize(),
                    entry.uncompressedSize(), MsDosDateTime.of(time), entry.hostSystem(),
                    entry.externalAttributes(), entry.comment());
        }
    }

    /** Sets the POSIX mode as UNIX external attributes. APPNOTE.TXT §4.4.15. */
    record SetUnixMode(int mode) implements EntryOperation {
        @Override
        public StoredEntry apply(StoredEntry entry) {
            return new StoredEntry(entry.name(), entry.method(), entry.crc32(), entry.compressedSize(),
                    entry.uncompressedSize(), entry.dosTime(), ZipFormat.HOST_UNIX,
                    UnixMode.externalAttributes(mode), entry.comment());
        }
    }
}
