package com.geno1024.ai.freezip

/**
 * Constants and record sizes from the ZIP specification.
 *
 * All section references point at PKWARE's `APPNOTE.TXT` (the canonical
 * `.ZIP File Format Specification`, also mirrored as Info-ZIP's `appnote.txt`).
 * Encrypted and split archives are intentionally out of scope.
 */
object ZipFormat {

    // Signatures. APPNOTE.TXT §4.3.7, §4.3.12, §4.3.14, §4.3.15, §4.3.16.
    const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
    const val CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE = 0x02014b50L
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE = 0x06064b50L
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIGNATURE = 0x07064b50L
    const val END_OF_CENTRAL_DIRECTORY_RECORD_SIGNATURE = 0x06054b50L

    // Fixed record sizes in bytes.
    const val LOCAL_FILE_HEADER_FIXED_SIZE = 30                        // §4.3.7
    const val CENTRAL_DIRECTORY_FILE_HEADER_FIXED_SIZE = 46            // §4.3.12
    const val END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE = 22          // §4.3.16
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_FIXED_SIZE = 56    // §4.3.14
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_SIZE = 20         // §4.3.15

    // "Version needed to extract". APPNOTE.TXT §4.4.3.
    const val VERSION_NEEDED_STORED = 10
    const val VERSION_NEEDED_DEFLATED = 20
    const val VERSION_NEEDED_ZIP64 = 45

    // "Version made by" host byte. APPNOTE.TXT §4.4.2.2.
    const val HOST_DOS = 0
    const val HOST_UNIX = 3

    // Overflow sentinels for 16- and 32-bit fields. APPNOTE.TXT §4.4.1.4.
    const val UINT16_MAX = 0xFFFF
    const val UINT32_MAX = 0xFFFFFFFFL
}
