package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

fun FORM.generalFields(data: CreateIdeaWizardSession) {
    div {
        id = "create-idea-general-fields"
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
            + "Litematic file, if you have one"
        }
        input {
            type = InputType.file
            id = "idea-litematic-file"
            name = "litematicFile"
            classes += "form-control"
            hxPost("/app/ideas/create/litematic")
            hxTarget("#create-idea-general-fields")
            hxSwap("innerHTML")
            hxTrigger("change")
            attributes["hx-encoding"] = "multipart/form-data"
        }
        p {
            id = "idea-litematic-response"
            if (data.litematicaFileName != null) {
                +"Uploaded Litematic: ${data.litematicaFileName} (${data.itemRequirements?.entries?.sumOf { it.value } ?: "Unknown amount of"} items and blocks to collect)"
            }
        }
        p("validation-error-message") {
            id = "validation-error-litematicFile"
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
}