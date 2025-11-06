package app.mcorg.pipeline.idea.createfragments

import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetVersionFields() {
    val rangeType = request.queryParameters["versionRangeType"] ?: "lowerBounded"

    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    respondHtml(createHTML().div {
        classes += "stack stack--xs"

        when (rangeType) {
            "bounded" -> {
                // From and To versions
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
                            +version.toString()
                        }
                    }
                }

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
                            +version.toString()
                        }
                    }
                }
            }
            "lowerBounded" -> {
                // From version onwards
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
                            +version.toString()
                        }
                    }
                }
                small("form-help-text subtle") {
                    +"Works from this version onwards"
                }
            }
            "upperBounded" -> {
                // Up to version
                label {
                    htmlFor = "version-to"
                    +"Up To Version"
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
                            +version.toString()
                        }
                    }
                }
                small("form-help-text subtle") {
                    +"Works up to and including this version"
                }
            }
            "unbounded" -> {
                // All versions
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
    })
}