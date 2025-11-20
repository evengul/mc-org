package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun FORM.generalFields(data: CreateIdeaWizardData) {
    label {
        htmlFor = "idea-name"
        +"Name"
        span("required-indicator") { +"*" }
    }
    input {
        id = "idea-name"
        name = "name"
        type = InputType.text
        classes += "form-control"
        required = true
        minLength = "3"
        maxLength = "100"
        if (data.name != null) {
            value = data.name
        }
        placeholder = "e.g., High-Speed Sugar Cane Farm"
    }
    p("validation-error-message") {
        id = "validation-error-name"
    }

    label {
        htmlFor = "idea-description"
        +"Description"
        span("required-indicator") { +"*" }
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
        if (data.description != null) {
            + data.description
        }
    }
    p("validation-error-message") {
        id = "validation-error-description"
    }

    label {
        htmlFor = "idea-difficulty"
        +"Difficulty"
        span("required-indicator") { +"*" }
    }
    select {
        id = "idea-difficulty"
        name = "difficulty"
        classes += "form-control"
        required = true
        IdeaDifficulty.entries.forEach { difficulty ->
            option {
                if (data.difficulty != null) {
                    selected = data.difficulty == difficulty
                }
                value = difficulty.name
                selected = difficulty == IdeaDifficulty.MID_GAME
                +difficulty.toPrettyEnumName()
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-difficulty"
    }
}