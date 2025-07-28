package app.mcorg.presentation.templated.utils

fun <T : Enum<*>> T?.toPrettyEnumName(): String {
    return this?.name?.split('_')?.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    } ?: ""
}