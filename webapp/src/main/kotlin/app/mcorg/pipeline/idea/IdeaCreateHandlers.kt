package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.presentation.templated.idea.renderCreateField
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * HTMX endpoint: Returns category-specific fields for the create form.
 * Triggered when user selects a category in the create modal.
 */
suspend fun ApplicationCall.handleGetCreateCategoryFields() {
    val categoryParam = request.queryParameters["category"]?.uppercase() ?: run {
        respondHtml(createHTML().div {
            p("subtle") {
                style = "text-align: center; padding: var(--spacing-sm);"
                +"Select a category to see specific fields"
            }
        })
        return
    }

    try {
        val category = IdeaCategory.valueOf(categoryParam)
        val schema = IdeaCategorySchemas.getSchema(category)

        respondHtml(createHTML().div {
            classes += "stack stack--sm"

            // Render all fields from the schema (not just filterable ones)
            schema.fields.forEach { field ->
                renderCreateField(field)
            }

            // If no fields exist
            if (schema.fields.isEmpty()) {
                p("subtle") {
                    style = "text-align: center; padding: var(--spacing-sm);"
                    +"No additional fields for this category"
                }
            }
        })
    } catch (_: IllegalArgumentException) {
        // Invalid category name
        respondHtml(createHTML().div {
            p("subtle") {
                style = "text-align: center; padding: var(--spacing-sm);"
                +"Invalid category"
            }
        })
    }
}

/**
 * HTMX endpoint: Returns author fields based on author type (single vs team).
 */
suspend fun ApplicationCall.handleGetAuthorFields() {
    val authorType = request.queryParameters["authorType"] ?: "single"

    respondHtml(createHTML().div {
        when (authorType) {
            "single" -> {
                label {
                    htmlFor = "author-name"
                    +"Author Name"
                    span("required-indicator") { +"*" }
                }
                input {
                    id = "author-name"
                    name = "authorName"
                    type = InputType.text
                    classes += "form-control"
                    required = true
                    placeholder = "Your name or username"
                }
            }
            "team" -> {
                p("subtle") {
                    style = "margin-bottom: var(--spacing-sm);"
                    +"Add team members below. Each member should have a name, role, and contributions."
                }

                div {
                    id = "team-members-container"
                    classes += "stack stack--sm"

                    // Initial team member
                    div("team-member-row stack stack--xs") {
                        label { +"Member Name" }
                        input {
                            name = "teamMembers[0][name]"
                            type = InputType.text
                            classes += "form-control"
                            required = true
                            placeholder = "Team member name"
                        }

                        label { +"Role" }
                        input {
                            name = "teamMembers[0][role]"
                            type = InputType.text
                            classes += "form-control"
                            placeholder = "e.g., Lead Designer"
                        }

                        label { +"Contributions" }
                        input {
                            name = "teamMembers[0][contributions]"
                            type = InputType.text
                            classes += "form-control"
                            placeholder = "e.g., Design, Testing (comma-separated)"
                        }
                    }
                }

                button(type = ButtonType.button, classes = "btn btn--sm btn--neutral") {
                    style = "margin-top: var(--spacing-xs);"
                    attributes["onclick"] = "addTeamMember()"
                    +"+ Add Team Member"
                }
            }
        }
    })
}

/**
 * HTMX endpoint: Returns version fields based on range type.
 */
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

