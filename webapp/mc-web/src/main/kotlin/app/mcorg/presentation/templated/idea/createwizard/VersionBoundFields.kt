package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.dsl.RadioGroupOption
import app.mcorg.presentation.templated.dsl.radioGroup
import kotlinx.html.*

fun DIV.versionBoundFields(supportedVersions: List<MinecraftVersion.Release>, versionRange: MinecraftVersionRange? = null) {
    when(versionRange) {
        null -> {}
        is MinecraftVersionRange.Bounded -> {
            versionLowerBound(supportedVersions, versionRange.from)
            versionUpperBound(supportedVersions, versionRange.to)
        }
        is MinecraftVersionRange.UpperBounded -> {
            versionUpperBound(supportedVersions, versionRange.to)
        }
        is MinecraftVersionRange.LowerBounded -> {
            versionLowerBound(supportedVersions, versionRange.from)
        }
        is MinecraftVersionRange.Unbounded -> {
            p("subtle") {
                style = "text-align: center;"
                +"This idea works in all Minecraft versions"
            }
            input {
                type = InputType.hidden
                name = "versionRangeType"
                value = "unbounded"
            }
        }
    }
}

private fun DIV.versionLowerBound(supportedVersions: List<MinecraftVersion.Release>, selectedVersion: MinecraftVersion? = null) {
    label {
        htmlFor = "version-from"
        +"From Version"
        span("required-indicator") { +"*" }
    }
    select {
        id = "version-from"
        name = "versionFrom"
        classes += "form-control"
        required = true
        supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version == selectedVersion
                +version.toString()
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-versionFrom"
    }
}

private fun DIV.versionUpperBound(supportedVersions: List<MinecraftVersion.Release>, selectedVersion: MinecraftVersion? = null) {
    label {
        htmlFor = "version-to"
        +"To Version"
        span("required-indicator") { +"*" }
    }
    select {
        id = "version-to"
        name = "versionTo"
        classes += "form-control"
        required = true
        supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version == selectedVersion
                +version.toString()
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-versionTo"
    }
}