package com.geno1024.ai.freezip;

/**
 * The general purpose bit flag. APPNOTE.TXT §4.4.4.
 *
 * <p>Only the bits relevant to this library are declared. Bit 3 (data descriptor)
 * is never set because all sizes and the CRC-32 are known before writing.
 */
public final class GeneralPurposeBitFlag {

    private GeneralPurposeBitFlag() {
    }

    /** Bit 3: a data descriptor follows the file data. APPNOTE.TXT §4.4.4. */
    public static final int DATA_DESCRIPTOR = 1 << 3;

    /** Bit 11: the file name and comment are UTF-8 encoded. APPNOTE.TXT §4.4.4. */
    public static final int UTF8 = 1 << 11;
}
