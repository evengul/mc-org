package app.mcorg.pipeline.failure

/**
 * What a person reads for a field that failed, wherever it is shown: next to the field, in the
 * idea form's callout, or in the alert that stands in for a missing slot.
 *
 * The label comes from the parameter name, so `farmName` reads "Farm name" and
 * `categoryData.size.width` reads "Size › width". A [ValidationFailure.CustomValidation] carries
 * its own sentence and is shown as written.
 */
fun ValidationFailure.userMessage(): String {
    val label = fieldLabel(parameterName)
    return when (this) {
        is ValidationFailure.MissingParameter -> "$label is required."
        is ValidationFailure.InvalidFormat -> message ?: "$label has an invalid format."
        is ValidationFailure.InvalidLength -> when {
            minLength != null && maxLength != null -> "$label must be between $minLength and $maxLength characters."
            minLength != null -> "$label must be at least $minLength characters."
            maxLength != null -> "$label must be at most $maxLength characters."
            else -> "$label has an invalid length."
        }
        is ValidationFailure.InvalidValue -> when {
            allowedValues != null -> "$label must be one of: ${allowedValues.joinToString(", ")}."
            else -> "$label is not a valid value."
        }
        is ValidationFailure.OutOfRange -> when {
            min != null && max != null -> "$label must be between $min and $max."
            min != null -> "$label must be at least $min."
            max != null -> "$label must be at most $max."
            else -> "$label is out of range."
        }
        is ValidationFailure.CustomValidation -> message
    }
}

internal fun fieldLabel(parameterName: String): String {
    // `teamMembers[0][role]` is a member's role: the index is position, not a name.
    val dotted = parameterName.removePrefix("categoryData.").removeSuffix("[]")
        .replace(Regex("""\[\d+]"""), "")
        .replace(Regex("""\[([^\]]+)]"""), ".$1")
    val segments = dotted.split(".")
    return segments.mapIndexed { index, segment ->
        val words = segment
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[_-]+"), " ")
            .trim()
            .lowercase()
        if (index == 0) words.replaceFirstChar { it.uppercase() } else words
    }.joinToString(" › ")
}
