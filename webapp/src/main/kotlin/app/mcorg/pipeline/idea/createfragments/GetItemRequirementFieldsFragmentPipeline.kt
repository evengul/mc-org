package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.createsession.updateWizardSession
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.idea.validators.ValidateItemRequirementStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.createwizard.itemRequirementListEntry
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetItemRequirementFields() {
    val input = this.parameters

    executePipeline(
        onSuccess = {
            updateWizardSession {
                copy(
                    itemRequirements = itemRequirements?.toMutableMap()?.apply {
                        this[it.first.id] = it.second
                    } ?: mapOf(it.first.id to it.second)
                )
            }
            respondHtml(createHTML().li {
                itemRequirementListEntry(it.first, it.second)
            })
        }
    ) {
        value(input)
            .step(object : Step<Parameters, AppFailure, MinecraftVersionRange> {
                override suspend fun process(input: Parameters): Result<AppFailure, MinecraftVersionRange> {
                    return ValidateIdeaMinecraftVersionStep.process(input).mapError { AppFailure.ValidationError(it) }
                }
            })
            .step(GetItemsInVersionRangeStep)
            .value { input to it }
            .step(ValidateItemRequirementStep)
    }
}