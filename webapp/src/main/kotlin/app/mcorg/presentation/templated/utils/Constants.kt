package app.mcorg.presentation.templated.utils

import java.time.format.DateTimeFormatter

object SimpleDateFormat {
    const val FORMAT_STRING = "dd/MM/yyyy"
    val INSTANCE: DateTimeFormatter = DateTimeFormatter.ofPattern(FORMAT_STRING)
}