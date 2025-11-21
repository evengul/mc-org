package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import io.ktor.http.*

data class ValidateAllItemRequirementsStep(private val versionRange: MinecraftVersionRange) : Step<Parameters, List<ValidationFailure>, Map<Item, Int>> {
    override suspend fun process(input: Parameters): Result<List<ValidationFailure>, Map<Item, Int>> {
        val availableItems = GetItemsInVersionRangeStep.process(versionRange).getOrNull() ?: run {
            return Result.failure(listOf(ValidationFailure.CustomValidation("items", "Failed to retrieve items for the specified version range")))
        }

        val relevantFields = input.entries()
            .filter { (k, _) -> k.startsWith("itemRequirements.") }
            .map { (k, v) -> k.removePrefix("itemRequirements.") to v }
            .associate { (k, v) -> validateKey(k, availableItems) to validateAmount(k, v) }

        val errors = relevantFields.entries.filter { (k, v) -> k is Result.Failure || v is Result.Failure }
            .flatMap { entry ->
                val errorList = mutableListOf<ValidationFailure>()
                (entry.key as? Result.Failure)?.let { errorList.add(it.error) }
                (entry.value as? Result.Failure)?.let { errorList.add(it.error) }
                errorList
            }

        return if (errors.isNotEmpty()) {
            Result.failure(errors)
        } else {
            val itemMap = relevantFields.entries.associate { (k, v) ->
                val item = (k as Result.Success).value
                val amount = (v as Result.Success).value
                item to amount
            }
            Result.success(itemMap)
        }
    }

    private fun validateKey(key: String, availableItems: List<Item>): Result<ValidationFailure, Item> {
        if (key.isEmpty()) {
            return Result.failure(ValidationFailure.InvalidFormat("hidden-item-requirement-item", "Item key cannot be empty"))
        }

        val item = availableItems.find { it.id == key }

        if (item == null) {
            return Result.failure(ValidationFailure.InvalidValue(key, availableItems.map { it.id }))
        }

        return Result.success(item)
    }

    private fun validateAmount(key: String, amount: List<String>): Result<ValidationFailure, Int> {
        if (amount.isEmpty()) {
            return Result.failure(ValidationFailure.InvalidFormat(key, "Amount is required"))
        }

        if (amount.size > 1) {
            return Result.failure(ValidationFailure.InvalidFormat(key, "Multiple amounts provided"))
        }

        val amt = amount.first().toIntOrNull()
            ?: return Result.failure(ValidationFailure.InvalidFormat(key, "Amount must be a valid integer"))

        if (amt !in 1..2000000000) {
            return Result.failure(ValidationFailure.OutOfRange(key, "Amount must be between 1 and 2,000,000,000"))
        }

        return Result.success(amt)
    }
}