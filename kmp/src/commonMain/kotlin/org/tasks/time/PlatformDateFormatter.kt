package org.tasks.kmp.org.tasks.time

expect fun currentLocaleTag(): String

expect class PlatformDateFormatter() {
    fun date(timestamp: Long, style: DateStyle): String

    /** A fixed date pattern in the current locale, e.g. "dd.MM.yyyy". */
    fun pattern(timestamp: Long, pattern: String): String

    fun time(timestamp: Long, is24HourFormat: Boolean): String

    fun fullDateTime(timestamp: Long, is24HourFormat: Boolean, dateStyle: DateStyle): String

    fun dayOfWeek(timestamp: Long, style: TextStyle): String
}
