package com.geno1024.ai.freezip;

/**
 * Compression methods supported by this library.
 *
 * <p>Only the two methods that every mainstream extractor understands are
 * offered: {@code stored} and {@code deflated}. See APPNOTE.TXT §4.4.5
 * (compression method) and §4.4.3 (version needed to extract).
 */
public enum CompressionMethod {

    /** Method 0: no compression. APPNOTE.TXT §4.4.5. */
    STORED(0, ZipFormat.VERSION_NEEDED_STORED),

    /** Method 8: raw DEFLATE. APPNOTE.TXT §4.4.5. */
    DEFLATED(8, ZipFormat.VERSION_NEEDED_DEFLATED);

    private final int id;
    private final int versionNeeded;

    CompressionMethod(int id, int versionNeeded) {
        this.id = id;
        this.versionNeeded = versionNeeded;
    }

    /** Compression method id as stored in the header. */
    public int id() {
        return id;
    }

    /** Minimum "version needed to extract" for this method. */
    public int versionNeeded() {
        return versionNeeded;
    }

    /**
     * Resolves a compression method id.
     *
     * @throws IllegalArgumentException if the id is not supported
     */
    public static CompressionMethod fromId(int id) {
        for (CompressionMethod method : values()) {
            if (method.id == id) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported compression method: " + id);
    }
}
