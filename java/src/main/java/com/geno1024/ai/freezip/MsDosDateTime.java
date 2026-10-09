package com.geno1024.ai.freezip;

import java.time.LocalDateTime;

/**
 * MS-DOS date and time fields used by ZIP headers.
 *
 * <p>APPNOTE.TXT §4.4.6 (date/time fields). The encoding has a two-second
 * resolution and no time zone, and cannot represent years before 1980.
 *
 * <pre>
 * date: bits 0..4 day, 5..8 month, 9..15 year - 1980
 * time: bits 0..4 second / 2, 5..10 minute, 11..15 hour
 * </pre>
 */
public record MsDosDateTime(int time, int date) {

    /**
     * Encodes a date/time into the MS-DOS representation, clamping to the
     * representable range (years are clamped to 1980 and later).
     */
    public static MsDosDateTime of(LocalDateTime value) {
        int year = Math.max(value.getYear() - 1980, 0);
        int month = value.getMonthValue();
        int day = value.getDayOfMonth();
        int hour = value.getHour();
        int minute = value.getMinute();
        int second = value.getSecond() / 2;

        int dosDate = ((year & 0x7F) << 9) | ((month & 0x0F) << 5) | (day & 0x1F);
        int dosTime = ((hour & 0x1F) << 11) | ((minute & 0x3F) << 5) | (second & 0x1F);
        return new MsDosDateTime(dosTime, dosDate);
    }

    public LocalDateTime toLocalDateTime() {
        int year = ((date >>> 9) & 0x7F) + 1980;
        int month = (date >>> 5) & 0x0F;
        int day = date & 0x1F;
        int hour = (time >>> 11) & 0x1F;
        int minute = (time >>> 5) & 0x3F;
        int second = (time & 0x1F) * 2;
        return LocalDateTime.of(year, Math.max(month, 1), Math.max(day, 1),
                hour, minute, second);
    }
}
