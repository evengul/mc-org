package app.mcorg.presentation.templated.utils

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Centralized datetime formatting utilities for consistent UX across the application.
 *
 * Usage: timestamp.formatAsDate(), timestamp.formatAsDateTime(), etc.
 */

private object DateTimeFormatters {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
}

/**
 * Formats a ZonedDateTime as a short date: "21/10/2025"
 */
fun ZonedDateTime.formatAsDate(): String = this.format(DateTimeFormatters.DATE)

/**
 * Formats a ZonedDateTime with date and time (compact): "21/10/2025 14:30"
 */
fun ZonedDateTime.formatAsDateTime(): String = this.format(DateTimeFormatters.DATETIME)

/**
 * Formats a ZonedDateTime as relative time if less than 7 days ago, otherwise as short date.
 * Examples: "2 days ago", "3 hours ago", "Just now", or "21/10/2025"
 */
fun ZonedDateTime.formatAsRelativeOrDate(): String {
    val now = ZonedDateTime.now()
    val duration = Duration.between(this, now)

    return when {
        duration.toDays() >= 7 -> this.formatAsDate()
        duration.toDays() > 0 -> "${duration.toDays()} day${if (duration.toDays() == 1L) "" else "s"} ago"
        duration.toHours() > 0 -> "${duration.toHours()} hour${if (duration.toHours() == 1L) "" else "s"} ago"
        duration.toMinutes() > 0 -> "${duration.toMinutes()} minute${if (duration.toMinutes() == 1L) "" else "s"} ago"
        else -> "Just now"
    }
}

