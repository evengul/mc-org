package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.idea.schema.IdeaCategorySchema
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

data class ValidateIdeaCategoryDataStep(private val category: IdeaCategory): Step<Parameters, List<ValidationFailure>, Map<String, CategoryValue>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val schema = IdeaCategorySchemas.getSchema(category)

    override suspend fun process(input: Parameters): Result<List<ValidationFailure>, Map<String, CategoryValue>> {
        val categoryData = mutableMapOf<String, CategoryValue>()
        val errors = mutableListOf<ValidationFailure>()
        val processedFields = mutableSetOf<String>()

        val versionRange = ValidateIdeaMinecraftVersionStep.process(input).getOrNull() ?: MinecraftVersionRange.Unbounded

        val mainKeys = input.entries()
            .filter { (k, _) -> k.startsWith("categoryData.") }
            .map { (k, _) -> k.removePrefix("categoryData.") }
            .map { k -> if (k.contains(".")) k.split(".").first() else k }
            .toSet()
            .mapNotNull { k -> schema.getFieldOrLogUnknown(k) }

        for (field in mainKeys) {
            when (val validationResult = input.validateValue(versionRange, field)) {
                is Result.Success -> {
                    if (
                        (field is CategoryField.MultiSelect && (validationResult.value as? CategoryValue.MultiSelectValue)?.values?.isNotEmpty() == true) ||
                        (field.isBottomLevelField && field !is CategoryField.MultiSelect && validationResult.value != CategoryValue.IgnoredValue) ||
                        (field is CategoryField.StructField && (validationResult.value as? CategoryValue.MapValue)?.value?.isNotEmpty() == true) ||
                        (field is CategoryField.TypedMapField && (validationResult.value as? CategoryValue.MapValue)?.value?.isNotEmpty() == true)
                    ) {
                        categoryData[field.key] = validationResult.value
                    }
                }
                is Result.Failure -> {
                    errors.addAll(validationResult.error)
                }
            }
            processedFields.add(field.key)
        }

        // Validate required fields are present
        schema.fields.forEach { field ->
            if (field.required && !processedFields.contains(field.key) && errors.none { it is ValidationFailure.MissingParameter && it.parameterName.contains(field.key) }) {
                errors.add(ValidationFailure.MissingParameter(field.key))
            }
        }

        input.entries()
            .filter { it.key.startsWith("categoryData.") }
            .map { it.key.removePrefix("categoryData.") }
            .map { k -> if (k.contains(".")) k.split(".").first() else k }
            .map { it.removeSuffix("[]") }
            .distinct()
            .filter { processedFields.none { field -> field.contains(it) } }
            .forEach {
                errors.add(ValidationFailure.CustomValidation(it, "Unknown field provided: $it"))
            }

        return if (errors.isNotEmpty()) {
            Result.Failure(errors)
        } else {
            Result.Success(categoryData)
        }
    }

    private fun IdeaCategorySchema.getFieldOrLogUnknown(key: String): CategoryField? {
        val field = if (key.endsWith("[]")) {
            this.getField(key.removeSuffix("[]"))
        } else {
            this.getField(key)
        }
        if (field == null) {
            logUnknownField(key)
        }
        return field
    }

    private fun logUnknownField(fieldKey: String) {
        logger.debug("Encountered unknown category data field: {} for category: {}", fieldKey, category)
    }

    private fun Parameters.validateValue(
        versionRange: MinecraftVersionRange,
        field: CategoryField,
        values: List<String> = getAllIgnoringSuffix(field.getCompleteKey())
    ): Result<List<ValidationFailure>, CategoryValue> {
        if (field.isBottomLevelField) {
            val bottomLevelValidation = validateBottomLevelField(values, field)
            if (bottomLevelValidation is Result.Success && bottomLevelValidation.value == BottomLevelValidationResult.Ignored) {
                return Result.Success(CategoryValue.IgnoredValue)
            } else if (bottomLevelValidation is Result.Failure) {
                return Result.Failure(listOf(bottomLevelValidation.error))
            }
        }

        return runBlocking {
            when (field) {
                is CategoryField.MultiSelect -> validateMultiSelect(values, field)
                is CategoryField.Text -> validateText(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.Number -> validateNumber(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.Percentage -> validatePercentage(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.Rate -> validateRate(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.BooleanField -> validateBoolean(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.Select -> validateSelect(versionRange, values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.ListField -> validateListField(values.firstOrNull() ?: "", field).mapError { listOf(it) }
                is CategoryField.StructField -> validateStructField(versionRange, this@validateValue, field)
                is CategoryField.TypedMapField -> validateTypedMapField(versionRange, this@validateValue, field)
            }
        }
    }

    private fun Parameters.getAllIgnoringSuffix(key: String): List<String> {
        val attemptOne = this.getAll(key)
        if (attemptOne != null) {
            logger.debug("Found parameter values for key: {} with suffix included", key)
            return attemptOne
        }
        val attemptTwo = this.getAll(key.removeSuffix("[]"))
        if (attemptTwo != null) {
            logger.debug("Found parameter values for key: {} with suffix removed", key.removeSuffix("[]"))
            return attemptTwo
        }
        logger.debug("No parameter values found for key: {} or with suffix removed", key)
        return emptyList()
    }

    enum class BottomLevelValidationResult {
        Usable,
        Ignored
    }

    private fun validateBottomLevelField(values: List<String>, field: CategoryField): Result<ValidationFailure, BottomLevelValidationResult> {
        if (field is CategoryField.MultiSelect) {
            return if (field.required && values.isEmpty()) {
                Result.failure(ValidationFailure.MissingParameter(field.getCompleteKey()))
            } else {
                Result.success(BottomLevelValidationResult.Usable)
            }
        }

        if (values.size > 1 && !(field.parents.isNotEmpty() && field.parents.first() is CategoryField.TypedMapField)) {
            return Result.failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Expected single value for ${field.key}, but got multiple"))
        }

        if (values.size == 1) {
            if (values.first().isNotBlank()) {
                return Result.success(BottomLevelValidationResult.Usable)
            } else if (field.required) {
                return Result.failure(ValidationFailure.MissingParameter(field.getCompleteKey()))
            }
        }

        return if (field.required) {
            Result.failure(ValidationFailure.MissingParameter(field.getCompleteKey()))
        } else {
            Result.success(BottomLevelValidationResult.Ignored)
        }
    }

    private fun validateBoolean(value: String, field: CategoryField.BooleanField): Result<ValidationFailure, CategoryValue.BooleanValue> {
        return when (value.trim()) {
            "true", "1", "on" -> Result.Success(CategoryValue.BooleanValue(true))
            "false", "0", "off" -> Result.Success(CategoryValue.BooleanValue(false))
            else -> Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Boolean value expected, got: $value"))
        }
    }

    private fun validateListField(value: String, field: CategoryField.ListField): Result<ValidationFailure, CategoryValue.MultiSelectValue> {
        val values = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (field.required && values.isEmpty()) {
            return Result.failure(ValidationFailure.MissingParameter(field.getCompleteKey()))
        }
        return Result.Success(CategoryValue.MultiSelectValue(values))
    }

    private fun validateTypedMapField(versionRange: MinecraftVersionRange, input: Parameters, field: CategoryField.TypedMapField): Result<List<ValidationFailure>, CategoryValue.MapValue> {
        val map = mutableMapOf<String, CategoryValue>()
        val errors = mutableListOf<ValidationFailure>()

        val keys = input.getAllIgnoringSuffix(field.keyType.getCompleteKey())
        val values = input.getAllIgnoringSuffix(field.valueType.getCompleteKey())

        if (field.valueType !is CategoryField.TypedMapField && keys.size != values.size) {
            return Result.Failure(listOf(ValidationFailure.InvalidFormat(field.key, "Mismatched number of keys and values for typed map field '${field.key}'")))
        }

        for (i in keys.indices) {
            if (keys[i].isBlank()) continue
            val keyResult = input.validateValue(versionRange, field.keyType, listOf(keys[i]))
            val valueResult = if (field.valueType is CategoryField.TypedMapField) validateTypedMapField(versionRange, input, field.valueType)
                                else input.validateValue(versionRange, field.valueType, listOf(values[i]))

            when (keyResult) {
                is Result.Success -> {
                    when (valueResult) {
                        is Result.Success -> {
                            if (keyResult.value !is CategoryValue.IgnoredValue) {
                                map[keys[i]] = valueResult.value
                            }
                        }
                        is Result.Failure -> {
                            errors.addAll(valueResult.error)
                        }
                    }
                }
                is Result.Failure -> {
                    errors.addAll(keyResult.error)
                }
            }
        }

        return if (errors.isNotEmpty()) {
            Result.Failure(errors.toList())
        } else {
            Result.Success(CategoryValue.MapValue(map))
        }
    }

    private fun validateStructField(versionRange: MinecraftVersionRange, input: Parameters, field: CategoryField.StructField): Result<List<ValidationFailure>, CategoryValue.MapValue> {
        val struct = mutableMapOf<String, CategoryValue>()
        val errors = mutableListOf<ValidationFailure>()
        field.fields.forEach {
            when (val result = input.validateValue(versionRange, it)) {
                is Result.Success -> {
                    if (result.value != CategoryValue.IgnoredValue) {
                        struct[it.key] = result.value
                    }
                }
                is Result.Failure -> {
                    errors.addAll(result.error)
                }
            }
        }

        return if (errors.isNotEmpty()) {
            Result.Failure(errors)
        } else {
            Result.Success(CategoryValue.MapValue(struct))
        }
    }

    private fun validateMultiSelect(values: List<String>, field: CategoryField.MultiSelect): Result<List<ValidationFailure>, CategoryValue.MultiSelectValue> {
        val results = values.filter { it.isNotBlank() }.map { value ->
            if (field.options.contains(value)) {
                Result.Success(value)
            } else {
                Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Invalid option for multi-select field '${field.key}': $value"))
            }
        }

        val failures = results.filter { it is Result.Failure }.map { (it as Result.Failure).error }

        return if (failures.isNotEmpty()) {
            Result.Failure(failures)
        } else {
            Result.Success(CategoryValue.MultiSelectValue(
                results.filterIsInstance<Result.Success<String>>().map { it.value }.toSet()
            ))
        }
    }

    private fun validateNumber(value: String, field: CategoryField.Number): Result<ValidationFailure, CategoryValue.IntValue> {
        val intValue = value.toIntOrNull()
        return if (intValue != null) {
            if (field.min != null && intValue < field.min) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Number value for '${field.key}' must be at least ${field.min}, got: $intValue"))
            }
            if (field.max != null && intValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Number value for '${field.key}' must be at most ${field.max}, got: $intValue"))
            }
            Result.Success(CategoryValue.IntValue(intValue))
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Number value expected, got: $value"))
        }
    }

    private fun validatePercentage(value: String, field: CategoryField.Percentage): Result<ValidationFailure, CategoryValue.IntValue> {
        val intValue = value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
        return if (intValue != null) {
            if (intValue < field.min || intValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Percentage value for '${field.key}' must be between ${field.min} and ${field.max}, got: $intValue"))
            }
            Result.Success(CategoryValue.IntValue(intValue))
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Percentage value expected, got: $value"))
        }
    }

    private fun validateRate(value: String, field: CategoryField.Rate): Result<ValidationFailure, CategoryValue.IntValue> {
        val intValue = value.toIntOrNull()
        return if (intValue != null) {
            if (field.min != null && intValue < field.min) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Rate value for '${field.key}' must be at least ${field.min.toInt()}, got: $intValue"))
            }
            if (field.max != null && intValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Rate value for '${field.key}' must be at most ${field.max.toInt()}, got: $intValue"))
            }
            Result.Success(CategoryValue.IntValue(intValue))
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Rate value expected, got: $value"))
        }
    }

    private fun validateSelect(versionRange: MinecraftVersionRange, value: String, field: CategoryField.Select): Result<ValidationFailure, CategoryValue> {
        return field.options(versionRange)
            .find { it.value == value }
            ?.let { Result.Success(CategoryValue.TextValue(it.value)) }
            ?: Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Invalid option for select field '${field.key}': $value"))
    }

    private fun validateText(value: String, field: CategoryField.Text): Result<ValidationFailure, CategoryValue.TextValue> {
        if (value.length > (field.maxLength ?: Int.MAX_VALUE)) {
            return Result.Failure(ValidationFailure.InvalidFormat(field.getCompleteKey(), "Text value for '${field.key}' exceeds maximum length of ${field.maxLength}, got length: ${value.length}"))
        }
        return Result.Success(CategoryValue.TextValue(value))
    }
}