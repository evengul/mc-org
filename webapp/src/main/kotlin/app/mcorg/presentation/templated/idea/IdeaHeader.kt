package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun HEADER.ideasHeader(user: TokenProfile) {
    id = "ideas-header"
    div {
        id = "ideas-header-start"
        h1 {
            +"Idea Bank"
        }
        p {
            classes += "subtle"
            +"Browse and share Minecraft contraption ideas with the community"
        }
    }
    div {
        id = "ideas-header-end"
        if (user.isIdeaCreator) {
            createIdeaModal()
        }
    }
}

fun DIV.createIdeaModal() = formModal(
    modalId = "create-idea-modal",
    title = "Submit New Idea",
    description = "Share your Minecraft contraption idea with the community.",
    saveText = "Submit Idea",
    hxValues = FormModalHxValues(
        hxTarget = "#ideas-list",
        hxSwap = "afterbegin",
        method = FormModalHttpMethod.POST,
        href = "/app/ideas/create"
    ),
    openButtonBlock = {
        addClass("create-idea-button")
        addClass("btn--action")
        iconLeft = Icons.MENU_ADD
        iconSize = IconSize.SMALL
        + "Submit new idea"
    }
) {
    formContent {
        classes += "create-idea-form stack stack--sm"

        // Basic Information Section
        div("form-section") {
            h3 { +"Basic Information" }

            // Idea Name
            label {
                htmlFor = "idea-name"
                + "Name"
                span("required-indicator") { +" *" }
            }
            input {
                id = "idea-name"
                name = "name"
                type = InputType.text
                classes += "form-control"
                required = true
                minLength = "3"
                maxLength = "255"
                placeholder = "e.g., High-Speed Sugar Cane Farm"
            }

            // Description
            label {
                htmlFor = "idea-description"
                +"Description"
                span("required-indicator") { +" *" }
            }
            textArea {
                id = "idea-description"
                name = "description"
                classes += "form-control"
                required = true
                minLength = "20"
                maxLength = "5000"
                rows = "6"
                placeholder = "Describe your contraption, how it works, and what makes it unique..."
            }

            // Category Selection (triggers HTMX to load category-specific fields)
            label {
                htmlFor = "idea-category"
                +"Category"
                span("required-indicator") { +" *" }
            }
            div("category-select") {
                IdeaCategory.entries.forEach { category ->
                    label("filter-radio-label") {
                        input(type = InputType.radio) {
                            name = "category"
                            value = category.name
                            // HTMX attributes to load category-specific fields
                            attributes["hx-get"] = "/app/ideas/create/fields/${category.name.lowercase()}"
                            attributes["hx-target"] = "#category-specific-fields"
                            attributes["hx-swap"] = "innerHTML"
                            attributes["hx-trigger"] = "change"
                        }
                        +category.toPrettyEnumName()
                    }
                }
            }

            // Difficulty
            label {
                htmlFor = "idea-difficulty"
                +"Difficulty"
                span("required-indicator") { +" *" }
            }
            select {
                id = "idea-difficulty"
                name = "difficulty"
                classes += "form-control"
                required = true
                IdeaDifficulty.entries.forEach { difficulty ->
                    option {
                        value = difficulty.name
                        selected = difficulty == IdeaDifficulty.MID_GAME
                        +difficulty.toPrettyEnumName()
                    }
                }
            }

            // Labels/Tags
            label {
                htmlFor = "idea-labels"
                +"Labels"
            }
            input {
                id = "idea-labels"
                name = "labels"
                type = InputType.text
                classes += "form-control"
                placeholder = "redstone, farm, automation (comma-separated)"
            }
            small("form-help-text subtle") {
                +"Add labels to help others find your idea"
            }
        }

        // Author Information Section
        div("form-section") {
            h3 { +"Author Information" }

            // Author Type
            label {
                +"Author Type"
                span("required-indicator") { +" *" }
            }
            div("author-type-select") {
                radioGroup(
                    "authorType",
                    listOf(
                        RadioGroupOption("single", "Single Author"),
                        RadioGroupOption("team", "Team")
                    ),
                    selectedOption = "single"
                ) {
                    block = {
                        attributes["hx-get"] = "/app/ideas/create/author-fields"
                        attributes["hx-target"] = "#author-fields"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-trigger"] = "change"
                        attributes["hx-vals"] = "js:{authorType: event.target.value}"
                    }
                }
            }

            // Dynamic author fields container
            div {
                id = "author-fields"
                // Default: single author field
                label {
                    htmlFor = "author-name"
                    +"Author Name"
                    span("required-indicator") { +" *" }
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

            // Sub-authors/Contributors
            label {
                htmlFor = "sub-authors"
                +"Contributors"
            }
            input {
                id = "sub-authors"
                name = "subAuthors"
                type = InputType.text
                classes += "form-control"
                placeholder = "Contributor1, Contributor2 (comma-separated)"
            }
            small("form-help-text subtle") {
                +"Credit others who contributed to this design"
            }
        }

        // Version Compatibility Section
        div("form-section") {
            h3 { +"Version Compatibility" }

            // Version Range Type
            label {
                +"Works in Minecraft Version"
                span("required-indicator") { +" *" }
            }
            div("version-range-type") {
                radioGroup(
                    "versionRangeType",
                    listOf(
                        RadioGroupOption("bounded", "Specific Range"),
                        RadioGroupOption("lowerBounded", "From Version Onwards"),
                        RadioGroupOption("upperBounded", "Up To Version"),
                        RadioGroupOption("unbounded", "All Versions")
                    ),
                    selectedOption = "lowerBounded"
                ) {
                    block = {
                        attributes["hx-get"] = "/app/ideas/create/version-fields"
                        attributes["hx-target"] = "#version-fields"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-trigger"] = "change"
                        attributes["hx-vals"] = "js:{versionRangeType: event.target.value}"
                    }
                }
            }

            // Dynamic version fields
            div {
                id = "version-fields"
                // Default: lower bounded (from version onwards)
                label {
                    htmlFor = "version-from"
                    +"From Version"
                    span("required-indicator") { +" *" }
                }
                select {
                    id = "version-from"
                    name = "versionFrom"
                    classes += "form-control"
                    required = true
                    MinecraftVersion.supportedVersions.forEach { version ->
                        option {
                            value = version.toString()
                            +version.toString()
                        }
                    }
                }
            }
        }

        // Performance Data Section (Optional)
        div("form-section") {
            h3 { +"Performance Data (Optional)" }

            label {
                htmlFor = "test-mspt"
                +"MSPT (Milliseconds Per Tick)"
            }
            input {
                id = "test-mspt"
                name = "testMspt"
                type = InputType.number
                classes += "form-control"
                attributes["step"] = "0.01"
                attributes["min"] = "0"
                placeholder = "e.g., 2.5"
            }

            label {
                htmlFor = "test-hardware"
                +"Hardware Description"
            }
            input {
                id = "test-hardware"
                name = "testHardware"
                type = InputType.text
                classes += "form-control"
                placeholder = "e.g., Intel i7-12700K, 32GB RAM"
            }

            label {
                htmlFor = "test-version"
                +"Tested in Version"
            }
            select {
                id = "test-version"
                name = "testVersion"
                classes += "form-control"
                option {
                    value = ""
                    +"Select version..."
                }
                MinecraftVersion.supportedVersions.forEach { version ->
                    option {
                        value = version.toString()
                        +version.toString()
                    }
                }
            }
        }

        // Category-Specific Fields (loaded dynamically via HTMX)
        div("form-section") {
            h3 { +"Category Details" }
            div {
                id = "category-specific-fields"
                classes += "stack stack--sm"
                // Will be populated by HTMX when category is selected
                p("subtle") {
                    style = "text-align: center; padding: var(--spacing-sm);"
                    +"Select a category above to see specific fields"
                }
            }
        }
    }
}
