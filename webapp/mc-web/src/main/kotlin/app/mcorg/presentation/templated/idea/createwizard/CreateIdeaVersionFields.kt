package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import kotlinx.html.*

fun FORM.versionFields(supportedVersions: List<MinecraftVersion.Release>, versionRange: MinecraftVersionRange? = null, ) {
    div("form-section") {
        h3 { +"Version Compatibility" }

        // Version Range Type
        label {
            +"Works in Minecraft Version"
            span("required-indicator") { +"*" }
        }
        div("version-range-type") {
            radioGroup(
                "versionRangeType",
                listOf(
                    RadioGroupOption("unbounded", "All Versions"),
                    RadioGroupOption("lowerBounded", "From Version Onwards"),
                    RadioGroupOption("upperBounded", "Up To Version"),
                    RadioGroupOption("bounded", "Specific Range")
                ),
                selectedOption = when(versionRange) {
                    is MinecraftVersionRange.Bounded -> "bounded"
                    is MinecraftVersionRange.LowerBounded -> "lowerBounded"
                    is MinecraftVersionRange.UpperBounded -> "upperBounded"
                    is MinecraftVersionRange.Unbounded -> "unbounded"
                    null -> "unbounded"
                }
            ) {
                block = {
                    attributes["hx-get"] = "/app/ideas/create/version-fields"
                    attributes["hx-target"] = "#version-fields"
                    attributes["hx-swap"] = "innerHTML"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-include"] = "#version-from, #version-to"
                    attributes["hx-vals"] = "js:{versionRangeType: event.target.value}"
                }
            }
        }
        p("validation-error-message") {
            id = "validation-error-versionRangeType"
        }

        // Dynamic version fields
        div {
            id = "version-fields"
            versionBoundFields(supportedVersions, versionRange)
        }
    }
}

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
                style = "text-align: center; padding: var(--spacing-sm);"
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