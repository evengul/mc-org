package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.http.*

object ValidateItemRequirementStep : Step<Pair<Parameters, List<Item>>, AppFailure.ValidationError, Pair<Item, Int>> {
    override suspend fun process(input: Pair<Parameters, List<Item>>): Result<AppFailure.ValidationError, Pair<Item, Int>> {
        val (parameters, availableItems) = input
        val itemId = ValidationSteps.required("itemId") { it }.process(parameters)
            .flatMap { exists -> ValidationSteps.validateAllowedValues("itemId", availableItems.map { it.id }, { it }, false).process(exists) }

        val amount = ValidationSteps.requiredInt("itemAmount") { it }.process(parameters)
            .flatMap { amt -> ValidationSteps.validateRange("itemAmount", 1, 2000000000) { it }.process(amt) }

        val errors = listOfNotNull(itemId.errorOrNull(), amount.errorOrNull())

        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            val item = availableItems.first { it.id == itemId.getOrNull()!! }
            Result.success(item to amount.getOrNull()!!)
        }
    }
}