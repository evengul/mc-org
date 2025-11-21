package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.idea.validators.ValidateItemRequirementStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.idea.createwizard.hiddenItemRequirementField
import app.mcorg.presentation.templated.idea.createwizard.itemRequirementListEntry
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetItemRequirementFields() {
    val input = this.parameters

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                itemRequirementListEntry(it.first, it.second)
            } + createHTML().div {
                hxOutOfBands("afterbegin:#hidden-item-requirements-fields")
                input {
                    hiddenItemRequirementField(it.first.id, it.second)
                }
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