package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*
import org.slf4j.LoggerFactory

data class ValidateIdeaCategoryDataStep(private val category: IdeaCategory): Step<Parameters, List<ValidationFailure>, Map<String, Any>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val schema = IdeaCategorySchemas.getSchema(category)

    override suspend fun process(input: Parameters): Result<List<ValidationFailure>, Map<String, Any>> {
        val categoryData = mutableMapOf<String, Any>()
        val errors = mutableListOf<ValidationFailure>()
        val processedFields = mutableSetOf<String>()

        for ((key, values) in input.entries()) {
            if (!(key.startsWith("categoryData[") && key.contains("]"))) {
                continue
            }

            // Parse nested structure: categoryData[field][subkey][type]
            // Regex captures: field name, optional first bracket content, optional second bracket content
            val pathMatch = Regex("""categoryData\[([^]]+)](?:\[([^]]+)])?(?:\[([^]]+)])?""").find(key)
            if (pathMatch == null) {
                logger.warn("Could not parse category data key: $key")
                continue
            }

            val (mainKey, subKey, typeKey) = pathMatch.destructured
            val field = schema.getField(mainKey)

            if (field == null) {
                logUnknownField(mainKey)
                continue
            }

            processedFields.add(mainKey)

            // Handle map fields with keyOptions: categoryData[field][option][value]
            if (subKey.isNotEmpty() && typeKey == "value") {
                if (field !is CategoryField.MapField) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "$mainKey is not a map field, but has been submitted as one"))
                    continue
                }

                if (field.keyOptions != null && !field.keyOptions.contains(subKey)) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "Invalid key '$subKey' for map field '$mainKey'"))
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val map = categoryData.getOrPut(mainKey) { mutableMapOf<String, String>() } as MutableMap<String, String>

                if (values.isNotEmpty() && values[0].isNotBlank()) {
                    map[subKey] = values[0]
                }
            }
            // Handle free-form map fields: categoryData[field][key][] or categoryData[field][value][]
            else if (subKey.isNotEmpty() && typeKey.isEmpty() && key.endsWith("[]")) {
                if (field !is CategoryField.MapField) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "$mainKey is not a map field, but has been submitted as one"))
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val map = categoryData.getOrPut(mainKey) { mutableMapOf<String, MutableList<String>>() } as MutableMap<String, MutableList<String>>

                // Store keys and values separately, will merge them later
                val list = map.getOrPut(subKey) { mutableListOf() }
                list.addAll(values)
            }
            // Handle array fields: categoryData[field][]
            else if (subKey.isEmpty() && key.endsWith("[]")) {
                if (field !is CategoryField.MultiSelect) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "$mainKey is not a multi-select or list field, but has been submitted as one"))
                    continue
                }

                val results = values.filter { it.isNotBlank() }.map { validateValue(it, field) }
                val listErrors = results.filterIsInstance<Result.Failure<ValidationFailure>>().map { it.error }
                if (listErrors.isNotEmpty()) {
                    errors.addAll(listErrors)
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val validValues = results.filterIsInstance<Result.Success<Any>>().mapNotNull { (it.value as List<String>).firstOrNull() }
                when (field) {
                    is CategoryField.MultiSelect -> categoryData[mainKey] = validValues.toSet()
                }
            }
            // Handle single value fields: categoryData[field]
            else if (subKey.isEmpty() && typeKey.isEmpty()) {
                if (field is CategoryField.MultiSelect || field is CategoryField.MapField) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "$mainKey is a multi-select or map field, but has been submitted as a single value"))
                    continue
                }
                if (values.size != 1) {
                    errors.add(ValidationFailure.InvalidFormat(mainKey, "Expected single value for $mainKey, but got multiple"))
                    continue
                }

                if (values[0].isBlank()) {
                    continue
                }

                when (val result = validateValue(values[0], field)) {
                    is Result.Success -> categoryData[mainKey] = result.value
                    is Result.Failure -> errors.add(result.error)
                }
            }
        }

        // Post-process free-form map fields to merge keys and values
        categoryData.entries.toList().forEach { (key, value) ->
            val field = schema.getField(key)
            if (field is CategoryField.MapField && field.keyOptions == null) {
                @Suppress("UNCHECKED_CAST")
                val tempMap = value as? Map<String, List<String>>
                if (tempMap != null) {
                    val keys = tempMap["key"] ?: emptyList()
                    val vals = tempMap["value"] ?: emptyList()

                    if (keys.size != vals.size) {
                        errors.add(ValidationFailure.InvalidFormat(key, "Map field '$key' has mismatched key-value pairs"))
                    } else {
                        val finalMap = keys.zip(vals).toMap().filterKeys { it.isNotBlank() }
                        categoryData[key] = finalMap
                    }
                }
            }
        }

        // Validate required fields are present
        schema.fields.forEach { field ->
            if (field.required && !processedFields.contains(field.key)) {
                errors.add(ValidationFailure.MissingParameter(field.key))
            }
        }

        return if (errors.isNotEmpty()) {
            Result.Failure(errors)
        } else {
            Result.Success(categoryData)
        }
    }

    private fun logUnknownField(fieldKey: String) {
        logger.debug("Encountered unknown category data field: {} for category: {}", fieldKey, category)
    }

    private fun validateValue(value: String, field: CategoryField): Result<ValidationFailure, Any> {
        return when (field) {
            is CategoryField.BooleanField -> validateBoolean(value, field)
            is CategoryField.ListField -> validateListField(value, field)
            is CategoryField.MapField -> validateMapField(field)
            is CategoryField.MultiSelect -> validateMultiSelect(value, field)
            is CategoryField.Number -> validateNumber(value, field)
            is CategoryField.Percentage -> validatePercentage(value, field)
            is CategoryField.Rate -> validateRate(value, field)
            is CategoryField.Select -> validateSelect(value, field)
            is CategoryField.Text -> validateText(value, field)
        }
    }

    private fun validateBoolean(value: String, field: CategoryField.BooleanField): Result<ValidationFailure, Boolean> {
        return when (value.trim()) {
            "true", "1", "on" -> Result.Success(true)
            "false", "0", "off" -> Result.Success(false)
            else -> Result.Failure(ValidationFailure.InvalidFormat(field.key, "Boolean value expected, got: $value"))
        }
    }

    private fun validateListField(value: String, field: CategoryField.ListField): Result<ValidationFailure, List<String>> {
        val values = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (field.required && values.isEmpty()) {
            return Result.failure(ValidationFailure.MissingParameter(field.key))
        }
        return Result.Success(values)
    }

    private fun validateMapField(field: CategoryField.MapField): Result<ValidationFailure, Nothing> {
        return Result.failure(ValidationFailure.CustomValidation(field.key, "Value should be a map, but received a single value"))
    }

    private fun validateMultiSelect(value: String, field: CategoryField.MultiSelect): Result<ValidationFailure, List<String>> {
        return if (field.options.contains(value)) {
            Result.Success(listOf(value))
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.key, "Invalid option for multi-select field '${field.key}': $value"))
        }
    }

    private fun validateNumber(value: String, field: CategoryField.Number): Result<ValidationFailure, Int> {
        val intValue = value.toIntOrNull()
        return if (intValue != null) {
            if (field.min != null && intValue < field.min) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Number value for '${field.key}' must be at least ${field.min}, got: $intValue"))
            }
            if (field.max != null && intValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Number value for '${field.key}' must be at most ${field.max}, got: $intValue"))
            }
            Result.Success(intValue)
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.key, "Number value expected, got: $value"))
        }
    }

    private fun validatePercentage(value: String, field: CategoryField.Percentage): Result<ValidationFailure, Double> {
        val doubleValue = value.toDoubleOrNull()
        return if (doubleValue != null) {
            if (doubleValue < field.min || doubleValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Percentage value for '${field.key}' must be between ${field.min} and ${field.max}, got: $doubleValue"))
            }
            Result.Success(doubleValue)
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.key, "Percentage value expected, got: $value"))
        }
    }

    private fun validateRate(value: String, field: CategoryField.Rate): Result<ValidationFailure, Int> {
        val doubleValue = value.toIntOrNull()
        return if (doubleValue != null) {
            if (field.min != null && doubleValue < field.min) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Rate value for '${field.key}' must be at least ${field.min.toInt()}, got: $doubleValue"))
            }
            if (field.max != null && doubleValue > field.max) {
                return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Rate value for '${field.key}' must be at most ${field.max.toInt()}, got: $doubleValue"))
            }
            Result.Success(doubleValue)
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.key, "Rate value expected, got: $value"))
        }
    }

    private fun validateSelect(value: String, field: CategoryField.Select): Result<ValidationFailure, String> {
        return if (field.options.contains(value)) {
            Result.Success(value)
        } else {
            Result.Failure(ValidationFailure.InvalidFormat(field.key, "Invalid option for select field '${field.key}': $value"))
        }
    }

    private fun validateText(value: String, field: CategoryField.Text): Result<ValidationFailure, String> {
        if (value.length > (field.maxLength ?: Int.MAX_VALUE)) {
            return Result.Failure(ValidationFailure.InvalidFormat(field.key, "Text value for '${field.key}' exceeds maximum length of ${field.maxLength}, got length: ${value.length}"))
        }
        return Result.Success(value)
    }
}