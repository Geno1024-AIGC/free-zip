package com.geno1024.ai.freezip

/**
 * General purpose bit flags (APPNOTE.TXT §4.4.4) used by this library.
 */
object GeneralPurposeBitFlag {
    /** Bit 3: sizes follow in a trailing data descriptor. Never set here. */
    const val DATA_DESCRIPTOR = 1 shl 3

    /** Bit 11: file name and comment are UTF-8. */
    const val UTF8 = 1 shl 11
}
