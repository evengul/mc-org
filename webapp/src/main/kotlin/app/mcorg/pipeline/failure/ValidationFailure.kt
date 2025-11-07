package app.mcorg.pipeline.failure

sealed interface ValidationFailure {
    val parameterName: String

    data class MissingParameter(override val parameterName: String) : ValidationFailure
    data class InvalidFormat(override val parameterName: String, val message: String? = null) : ValidationFailure
    data class OutOfRange(override val parameterName: String, val min: Any? = null, val max: Any? = null) : ValidationFailure
    data class InvalidLength(override val parameterName: String, val minLength: Int? = null, val maxLength: Int? = null) : ValidationFailure
    data class InvalidValue(override val parameterName: String, val allowedValues: List<String>? = null) : ValidationFailure
    data class CustomValidation(override val parameterName: String, val message: String) : ValidationFailure
}
