package com.geno1024.ai.freezip

/**
 * Options controlling archive assembly.
 *
 * @param zip64 how Zip64 records are emitted
 * @param comment optional archive comment (APPNOTE.TXT §4.3.16)
 */
data class AssembleOptions(val zip64: Zip64Mode, val comment: String) {

    /** Controls when the Zip64 end records are written. APPNOTE.TXT §4.3.14/15. */
    enum class Zip64Mode {
        /** Emit Zip64 records only when a field would overflow. */
        AUTO,

        /** Always emit Zip64 records; also saturates the classic EOCD fields. */
        ALWAYS,

        /** Never emit Zip64 records; oversized input is an error. */
        NEVER,
    }

    companion object {
        fun defaults(): AssembleOptions = AssembleOptions(Zip64Mode.AUTO, "")
    }
}
