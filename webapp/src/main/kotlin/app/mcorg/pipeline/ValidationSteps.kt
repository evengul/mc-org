package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("ValidationSteps")

object ValidationSteps {

    /**
     * Validates that a required parameter exists and is not blank
     */
    fun <E> required(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<Parameters, E, String> {
        return object : Step<Parameters, E, String> {
            override suspend fun process(input: Parameters): Result<E, String> {
                val value = input[parameterName]
                return if (value.isNullOrBlank()) {
                    logger.debug("Required parameter '$parameterName' is missing or blank")
                    Result.failure(errorMapper(ValidationFailure.MissingParameter(parameterName)))
                } else {
                    Result.success(value)
                }
            }
        }
    }

    /**
     * Validates an optional parameter, returning null if not present
     */
    fun optional(
        parameterName: String
    ): Step<Parameters, Nothing, String?> {
        return object : Step<Parameters, Nothing, String?> {
            override suspend fun process(input: Parameters): Result<Nothing, String?> {
                val value = input[parameterName]
                return Result.success(if (value.isNullOrBlank()) null else value)
            }
        }
    }

    /**
     * Validates that a parameter exists and converts it to an integer
     */
    fun <E> requiredInt(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<Parameters, E, Int> {
        return object : Step<Parameters, E, Int> {
            override suspend fun process(input: Parameters): Result<E, Int> {
                val value = input[parameterName]
                return if (value.isNullOrBlank()) {
                    logger.debug("Required integer parameter '$parameterName' is missing or blank")
                    Result.failure(errorMapper(ValidationFailure.MissingParameter(parameterName)))
                } else {
                    try {
                        Result.success(value.toInt())
                    } catch (_: NumberFormatException) {
                        logger.debug("Parameter '$parameterName' with value '$value' is not a valid integer")
                        Result.failure(errorMapper(ValidationFailure.InvalidFormat(parameterName, "Must be a valid integer")))
                    }
                }
            }
        }
    }

    /**
     * Validates an optional parameter and converts it to an integer
     */
    fun <E> optionalInt(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<Parameters, E, Int?> {
        return object : Step<Parameters, E, Int?> {
            override suspend fun process(input: Parameters): Result<E, Int?> {
                val value = input[parameterName]
                return if (value.isNullOrBlank()) {
                    Result.success(null)
                } else {
                    try {
                        Result.success(value.toInt())
                    } catch (_: NumberFormatException) {
                        logger.debug("Parameter '$parameterName' with value '$value' is not a valid integer")
                        Result.failure(errorMapper(ValidationFailure.InvalidFormat(parameterName, "Must be a valid integer")))
                    }
                }
            }
        }
    }

    /**
     * Validates that a parameter exists and converts it to a long
     */
    fun <E> requiredLong(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<Parameters, E, Long> {
        return object : Step<Parameters, E, Long> {
            override suspend fun process(input: Parameters): Result<E, Long> {
                val value = input[parameterName]
                return if (value.isNullOrBlank()) {
                    logger.debug("Required long parameter '$parameterName' is missing or blank")
                    Result.failure(errorMapper(ValidationFailure.MissingParameter(parameterName)))
                } else {
                    try {
                        Result.success(value.toLong())
                    } catch (_: NumberFormatException) {
                        logger.debug("Parameter '$parameterName' with value '$value' is not a valid long")
                        Result.failure(errorMapper(ValidationFailure.InvalidFormat(parameterName, "Must be a valid long integer")))
                    }
                }
            }
        }
    }

    /**
     * Validates that a parameter exists and converts it to a boolean
     */
    fun <E> requiredBoolean(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<Parameters, E, Boolean> {
        return object : Step<Parameters, E, Boolean> {
            override suspend fun process(input: Parameters): Result<E, Boolean> {
                val value = input[parameterName]
                return if (value.isNullOrBlank()) {
                    logger.debug("Required boolean parameter '$parameterName' is missing or blank")
                    Result.failure(errorMapper(ValidationFailure.MissingParameter(parameterName)))
                } else {
                    when (value.lowercase()) {
                        "true", "1", "yes", "on" -> Result.success(true)
                        "false", "0", "no", "off" -> Result.success(false)
                        else -> {
                            logger.debug("Parameter '$parameterName' with value '$value' is not a valid boolean")
                            Result.failure(errorMapper(ValidationFailure.InvalidFormat(parameterName, "Must be true/false, 1/0, yes/no, or on/off")))
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates that a string parameter meets length requirements
     */
    fun <E> validateLength(
        parameterName: String,
        minLength: Int? = null,
        maxLength: Int? = null,
        errorMapper: (ValidationFailure) -> E
    ): Step<String, E, String> {
        return object : Step<String, E, String> {
            override suspend fun process(input: String): Result<E, String> {
                val length = input.length
                return when {
                    minLength != null && length < minLength -> {
                        logger.debug("Parameter '$parameterName' length $length is below minimum $minLength")
                        Result.failure(errorMapper(ValidationFailure.InvalidLength(parameterName, minLength, maxLength)))
                    }
                    maxLength != null && length > maxLength -> {
                        logger.debug("Parameter '$parameterName' length $length exceeds maximum $maxLength")
                        Result.failure(errorMapper(ValidationFailure.InvalidLength(parameterName, minLength, maxLength)))
                    }
                    else -> Result.success(input)
                }
            }
        }
    }

    /**
     * Validates that a numeric parameter is within a specified range
     */
    fun <E, T : Comparable<T>> validateRange(
        parameterName: String,
        min: T? = null,
        max: T? = null,
        errorMapper: (ValidationFailure) -> E
    ): Step<T, E, T> {
        return object : Step<T, E, T> {
            override suspend fun process(input: T): Result<E, T> {
                return when {
                    min != null && input < min -> {
                        logger.debug("Parameter '{}' value {} is below minimum {}", parameterName, input, min)
                        Result.failure(errorMapper(ValidationFailure.OutOfRange(parameterName, min, max)))
                    }
                    max != null && input > max -> {
                        logger.debug("Parameter '{}' value {} exceeds maximum {}", parameterName, input, max)
                        Result.failure(errorMapper(ValidationFailure.OutOfRange(parameterName, min, max)))
                    }
                    else -> Result.success(input)
                }
            }
        }
    }

    /**
     * Validates that a parameter value is one of the allowed values
     */
    fun <E> validateAllowedValues(
        parameterName: String,
        allowedValues: List<String>,
        errorMapper: (ValidationFailure) -> E,
        ignoreCase: Boolean = false
    ): Step<String, E, String> {
        return object : Step<String, E, String> {
            override suspend fun process(input: String): Result<E, String> {
                val isValid = if (ignoreCase) {
                    allowedValues.any { it.equals(input, ignoreCase = true) }
                } else {
                    allowedValues.contains(input)
                }

                return if (isValid) {
                    Result.success(input)
                } else {
                    logger.debug(
                        "Parameter '{}' value '{}' is not in allowed values: {}",
                        parameterName,
                        input,
                        allowedValues
                    )
                    Result.failure(errorMapper(ValidationFailure.InvalidValue(parameterName, allowedValues)))
                }
            }
        }
    }

    /**
     * Validates using a custom predicate function
     */
    fun <E, T> validateCustom(
        parameterName: String,
        message: String,
        errorMapper: (ValidationFailure) -> E,
        predicate: suspend (T) -> Boolean
    ): Step<T, E, T> {
        return object : Step<T, E, T> {
            override suspend fun process(input: T): Result<E, T> {
                return if (predicate(input)) {
                    Result.success(input)
                } else {
                    logger.debug("Parameter '$parameterName' failed custom validation: $message")
                    Result.failure(errorMapper(ValidationFailure.CustomValidation(parameterName, message)))
                }
            }
        }
    }

    fun <E, T> validateNonNull(
        errorMapper: (ValidationFailure) -> E,
    ) : Step<T?, E, T> {
        return object : Step<T?, E, T> {
            override suspend fun process(input: T?): Result<E, T> {
                return if (input != null) {
                    Result.success(input)
                } else {
                    logger.debug("Parameter is null")
                    Result.failure(errorMapper(ValidationFailure.MissingParameter("Parameter is null")))
                }
            }
        }
    }

    /**
     * Validates an email format using a simple regex
     */
    fun <E> validateEmail(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<String, E, String> {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return validateCustom(
            parameterName,
            "Must be a valid email address",
            errorMapper
        ) { email: String -> emailRegex.matches(email) }
    }

    /**
     * Validates a URL format
     */
    fun <E> validateUrl(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E,
        allowedSchemes: List<String> = listOf("http", "https")
    ): Step<String, E, String> {
        return validateCustom(
            parameterName,
            "Must be a valid URL with scheme: ${allowedSchemes.joinToString(", ")}",
            errorMapper
        ) { url: String ->
            try {
                val parsedUrl = URI.create(url.trim()).toURL()
                allowedSchemes.contains(parsedUrl.protocol.lowercase())
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Validates a UUID format
     */
    fun <E> validateUuid(
        parameterName: String,
        errorMapper: (ValidationFailure) -> E
    ): Step<String, E, String> {
        return validateCustom(
            parameterName,
            "Must be a valid UUID",
            errorMapper
        ) { uuid: String ->
            try {
                java.util.UUID.fromString(uuid)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
}
