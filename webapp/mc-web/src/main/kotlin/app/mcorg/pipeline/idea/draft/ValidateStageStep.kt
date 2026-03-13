package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.validators.ValidateIdeaAuthorStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaCategoryDataStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaCategoryStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaDescriptionStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaDifficultyStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaNameStep
import app.mcorg.presentation.templated.idea.createwizard.DraftWizardStage
import io.ktor.http.Parameters

data class ValidateStageInput(val stage: DraftWizardStage, val params: Parameters)

/**
 * Validates the form parameters for a given wizard stage.
 * Returns a Success with the list of validation failures (may be empty).
 * Always succeeds — failures are represented as an empty or non-empty list, not a pipeline failure.
 */
object ValidateStageStep : Step<ValidateStageInput, AppFailure, List<ValidationFailure>> {
    override suspend fun process(input: ValidateStageInput): Result<AppFailure, List<ValidationFailure>> {
        val errors = when (input.stage) {
            DraftWizardStage.BASIC_INFO -> buildList {
                (ValidateIdeaNameStep.process(input.params) as? Result.Failure)?.let { add(it.error) }
                (ValidateIdeaDescriptionStep.process(input.params) as? Result.Failure)?.let { add(it.error) }
                (ValidateIdeaDifficultyStep.process(input.params) as? Result.Failure)?.let { add(it.error) }
            }
            DraftWizardStage.AUTHOR_INFO -> buildList {
                (ValidateIdeaAuthorStep.process(input.params) as? Result.Failure)?.let { add(it.error) }
            }
            DraftWizardStage.VERSION_COMPATIBILITY ->
                (ValidateIdeaMinecraftVersionStep.process(input.params) as? Result.Failure)?.error ?: emptyList()
            DraftWizardStage.ITEM_REQUIREMENTS -> emptyList()
            DraftWizardStage.CATEGORY_FIELDS -> buildList {
                val categoryResult = ValidateIdeaCategoryStep.process(input.params)
                if (categoryResult is Result.Failure) {
                    add(categoryResult.error)
                } else if (categoryResult is Result.Success) {
                    val dataResult = ValidateIdeaCategoryDataStep(categoryResult.value).process(input.params)
                    if (dataResult is Result.Failure) addAll(dataResult.error)
                }
            }
            DraftWizardStage.REVIEW -> emptyList()
        }
        return Result.success(errors)
    }
}
