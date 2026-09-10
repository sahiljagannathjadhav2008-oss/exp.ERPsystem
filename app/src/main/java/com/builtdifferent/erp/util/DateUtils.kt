package com.builtdifferent.erp.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * All persisted dates/timestamps in this app are stored as epoch millis
 * (UTC) longs — never as formatted strings — so that sorting, range
 * filtering (e.g. by financial year) and arithmetic in SQL work correctly.
 * These helpers are the single place formatting/parsing happens.
 */
object DateUtils {

    fun nowMillis(): Long = System.currentTimeMillis()

    fun todayStartMillis(): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun todayEndMillis(): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    fun formatDate(millis: Long, pattern: String = "dd-MM-yyyy"): String {
        val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long, pattern: String = "dd-MM-yyyy hh:mm a"): String {
        val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    /** Financial year in India runs 01-Apr to 31-Mar by default, but the
     * start month/day is configurable per Company (see FinancialYearEntity),
     * since some businesses use a calendar year instead. */
    fun financialYearLabel(startMillis: Long, endMillis: Long): String {
        val startYear = java.time.Instant.ofEpochMilli(startMillis)
            .atZone(ZoneId.systemDefault()).year
        val endYear = java.time.Instant.ofEpochMilli(endMillis)
            .atZone(ZoneId.systemDefault()).year
        return if (startYear == endYear) "FY $startYear" else "FY ${startYear}-${(endYear % 100).toString().padStart(2, '0')}"
    }
}
