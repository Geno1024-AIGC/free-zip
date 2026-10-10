package com.geno1024.ai.freezip;

import java.nio.charset.StandardCharsets;

/**
 * Options controlling archive assembly.
 *
 * @param zip64   how Zip64 records are emitted
 * @param comment optional archive comment (APPNOTE.TXT §4.3.16)
 */
public record AssembleOptions(Zip64Mode zip64, String comment) {

    /** Controls when the Zip64 end records are written. APPNOTE.TXT §4.3.14/15. */
    public enum Zip64Mode {
        /** Emit Zip64 records only when a field would overflow. */
        AUTO,
        /** Always emit Zip64 records; also saturates the classic EOCD fields. */
        ALWAYS,
        /** Never emit Zip64 records; oversized input is an error. */
        NEVER
    }

    public AssembleOptions {
        if (zip64 == null) {
            throw new IllegalArgumentException("zip64 mode must not be null");
        }
        if (comment == null) {
            comment = "";
        }
        if (comment.getBytes(StandardCharsets.UTF_8).length > ZipFormat.UINT16_MAX) {
            throw new IllegalArgumentException("archive comment too long (max 65535 UTF-8 bytes)");
        }
    }

    public static AssembleOptions defaults() {
        return new AssembleOptions(Zip64Mode.AUTO, "");
    }

    public AssembleOptions withZip64(Zip64Mode value) {
        return new AssembleOptions(value, comment);
    }

    public AssembleOptions withComment(String value) {
        return new AssembleOptions(zip64, value);
    }
}
