package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftVersionFields(draft: IdeaDraft, supportedVersions: List<MinecraftVersion.Release>) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val versionRange = data.versionRange

    val selectedType = when (versionRange) {
        is MinecraftVersionRange.Bounded -> "bounded"
        is MinecraftVersionRange.LowerBounded -> "lowerBounded"
        is MinecraftVersionRange.UpperBounded -> "upperBounded"
        else -> "unbounded"
    }

    div {
        label {
            htmlFor = "draft-version-type"
            +"Works in Minecraft Version"
            span("required-indicator") { +"*" }
        }
        select(classes = "form-control") {
            id = "draft-version-type"
            name = "versionRangeType"
            required = true
            hxGet("/ideas/create/version-fields")
            hxTrigger("change")
            hxTarget("#version-fields")
            hxSwap("innerHTML")
            attributes["hx-vals"] = "js:{versionRangeType: event.target.value}"
            option {
                value = "unbounded"
                selected = selectedType == "unbounded"
                +"All Versions"
            }
            option {
                value = "lowerBounded"
                selected = selectedType == "lowerBounded"
                +"From Version Onwards"
            }
            option {
                value = "upperBounded"
                selected = selectedType == "upperBounded"
                +"Up To Version"
            }
            option {
                value = "bounded"
                selected = selectedType == "bounded"
                +"Specific Range"
            }
        }
        p("form-error") { id = "error-versionRangeType" }
    }

    div {
        id = "version-fields"
        versionBoundFields(supportedVersions, versionRange)
    }
}
