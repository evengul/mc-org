package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.presentation.templated.idea.createwizard.versionBoundFields
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetVersionFields() {
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
    val versionRange = ValidateIdeaMinecraftVersionStep.process(parameters).recover {
        when (request.queryParameters["versionRangeType"]) {
            "bounded" -> Result.success(MinecraftVersionRange.Bounded(
                supportedVersions.sortedWith { a, b -> b.compareTo(a) }.first(),
                supportedVersions.sortedWith { a, b -> a.compareTo(b) }.first()
            ))
            "lowerBounded" -> Result.success(MinecraftVersionRange.LowerBounded(
                supportedVersions.sortedWith { a, b -> a.compareTo(b) }.first()
            ))
            "upperBounded" -> Result.success(MinecraftVersionRange.UpperBounded(
                supportedVersions.sortedWith { a, b -> b.compareTo(a) }.first()
            ))
            "unbounded" -> Result.success(MinecraftVersionRange.Unbounded)
            else -> Result.failure(it)
        }
    }.getOrNull()

    respondHtml(createHTML().div {
        classes += "stack stack--xs"

        versionBoundFields(supportedVersions, versionRange)
    })
}