package app.mcorg.pipeline.failure

sealed interface ValidationFailure {
    data class MissingParameter(val parameterName: String) : ValidationFailure
    data class InvalidFormat(val parameterName: String, val message: String? = null) : ValidationFailure
    data class OutOfRange(val parameterName: String, val min: Any? = null, val max: Any? = null) : ValidationFailure
    data class InvalidLength(val parameterName: String, val minLength: Int? = null, val maxLength: Int? = null) : ValidationFailure
    data class InvalidValue(val parameterName: String, val allowedValues: List<String>? = null) : ValidationFailure
    data class CustomValidation(val parameterName: String, val message: String) : ValidationFailure
}
