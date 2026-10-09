package com.geno1024.ai.freezip;

/**
 * Constants and record sizes from the ZIP specification.
 *
 * <p>All section references point at PKWARE's {@code APPNOTE.TXT} (the canonical
 * {@code .ZIP File Format Specification}), which is also mirrored as Info-ZIP's
 * {@code appnote.txt}. Only the classic records are declared here; encrypted and
 * split archives are intentionally out of scope.
 */
public final class ZipFormat {

    private ZipFormat() {
    }

    // Signatures. APPNOTE.TXT §4.3.7, §4.3.12, §4.3.14, §4.3.15, §4.3.16.
    public static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L;
    public static final long CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50L;
    public static final long ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE = 0x06064b50L;
    public static final long ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE = 0x07064b50L;
    public static final long END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE = 0x06054b50L;

    // Fixed record sizes in bytes.
    public static final int LOCAL_FILE_HEADER_FIXED_SIZE = 30;                     // §4.3.7
    public static final int CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE = 46;         // §4.3.12
    public static final int END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE = 22;       // §4.3.16
    public static final int ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE = 56; // §4.3.14
    public static final int ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE = 20;      // §4.3.15

    // "Version needed to extract". APPNOTE.TXT §4.4.3.
    public static final int VERSION_NEEDED_STORED = 10;
    public static final int VERSION_NEEDED_DEFLATED = 20;
    public static final int VERSION_NEEDED_ZIP64 = 45;

    // "Version made by" host byte. APPNOTE.TXT §4.4.2.2.
    public static final int HOST_DOS = 0;
    public static final int HOST_UNIX = 3;

    // Overflow sentinels for 16- and 32-bit fields. APPNOTE.TXT §4.4.1.4.
    public static final int UINT16_MAX = 0xFFFF;
    public static final long UINT32_MAX = 0xFFFFFFFFL;

    // General purpose bit flags. APPNOTE.TXT §4.4.4.
    public static final int FLAG_DATA_DESCRIPTOR = 1 << 3;
    public static final int FLAG_UTF8 = 1 << 11;

    // Extra field header IDs. APPNOTE.TXT §4.5.3 (Zip64), §4.5.2 (Info-ZIP Unix).
    public static final int EXTRA_FIELD_ZIP64 = 0x0001;
    public static final int EXTRA_FIELD_UNIX = 0x5855;
}
