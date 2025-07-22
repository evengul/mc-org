package app.mcorg.presentation.mockdata

import java.time.ZonedDateTime

private val zone = ZonedDateTime.now().zone

fun mockZonedDateTime(year: Int = 2023, month: Int = 2, dayOfMonth: Int = 15, hour: Int = 12, minute: Int = 0, second: Int = 0, nano: Int = 0) = ZonedDateTime.of(
    year, month, dayOfMonth, hour, minute, second, nano, zone
)