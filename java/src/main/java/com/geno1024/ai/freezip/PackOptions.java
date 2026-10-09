package com.geno1024.ai.freezip;

import java.util.zip.Deflater;

/**
 * Options controlling how a single file is packed into a stored entry.
 *
 * @param entryName        name stored inside the archive; defaults to the source
 *                         file name when {@code null}
 * @param method           compression method; {@link CompressionMethod#DEFLATED}
 *                         is recommended except for already-compressed payloads
 * @param compressionLevel DEFLATE level, see {@link Deflater}
 * @param comment          optional file comment (APPNOTE.TXT §4.3.12)
 */
public record PackOptions(
        String entryName,
        CompressionMethod method,
        int compressionLevel,
        String comment
) {

    public PackOptions {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
    }

    public static PackOptions defaults(String entryName) {
        return new PackOptions(entryName, CompressionMethod.DEFLATED, Deflater.DEFAULT_COMPRESSION, "");
    }

    public PackOptions withEntryName(String value) {
        return new PackOptions(value, method, compressionLevel, comment);
    }

    public PackOptions withMethod(CompressionMethod value) {
        return new PackOptions(entryName, value, compressionLevel, comment);
    }

    public PackOptions withCompressionLevel(int value) {
        return new PackOptions(entryName, method, value, comment);
    }

    public PackOptions withComment(String value) {
        return new PackOptions(entryName, method, compressionLevel, value);
    }
}
