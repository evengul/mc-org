package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.idea.validators.ValidateItemRequirementStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.createwizard.itemRequirementListEntry
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetItemRequirementFields() {
    val input = this.parameters

    handlePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                itemRequirementListEntry(it.first, it.second)
            })
        }
    ) {
        val versionRange = ValidateVersionRangeStep.run(input)
        val items = GetItemsInVersionRangeStep.run(versionRange)
        ValidateItemRequirementStep.run(input to items)
    }
}

private object ValidateVersionRangeStep : Step<Parameters, AppFailure, MinecraftVersionRange> {
    override suspend fun process(input: Parameters): Result<AppFailure, MinecraftVersionRange> {
        return ValidateIdeaMinecraftVersionStep.process(input).mapError { AppFailure.ValidationError(it) }
    }
}
