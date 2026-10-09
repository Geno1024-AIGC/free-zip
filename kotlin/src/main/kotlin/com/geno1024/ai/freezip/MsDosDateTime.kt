package com.geno1024.ai.freezip

import java.time.LocalDateTime

/**
 * MS-DOS date/time encoding used by ZIP headers.
 *
 * APPNOTE.TXT §4.4.6 (date/time fields). The format has a two-second resolution
 * and no time zone, and cannot represent years before 1980.
 *
 * ```
 * date: bits 0..4 day, 5..8 month, 9..15 year - 1980
 * time: bits 0..4 second / 2, 5..10 minute, 11..15 hour
 * ```
 */
data class MsDosDateTime(val time: Int, val date: Int) {

    fun toLocalDateTime(): LocalDateTime {
        val year = ((date ushr 9) and 0x7F) + 1980
        val month = (date ushr 5) and 0x0F
        val day = date and 0x1F
        val hour = (time ushr 11) and 0x1F
        val minute = (time ushr 5) and 0x3F
        val second = (time and 0x1F) * 2
        return LocalDateTime.of(year, maxOf(month, 1), maxOf(day, 1), hour, minute, second)
    }

    companion object {
        /** Encodes a date/time, clamping years to 1980 and later. */
        fun of(value: LocalDateTime): MsDosDateTime {
            val year = maxOf(value.year - 1980, 0)
            val dosDate = ((year and 0x7F) shl 9) or
                ((value.monthValue and 0x0F) shl 5) or
                (value.dayOfMonth and 0x1F)
            val dosTime = ((value.hour and 0x1F) shl 11) or
                ((value.minute and 0x3F) shl 5) or
                ((value.second / 2) and 0x1F)
            return MsDosDateTime(dosTime, dosDate)
        }
    }
}
