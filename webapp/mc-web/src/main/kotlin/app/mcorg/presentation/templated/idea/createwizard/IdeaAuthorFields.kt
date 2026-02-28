package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupLayout
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style

fun FORM.authorFields(data: CreateIdeaWizardSession) {
    div("form-section") {
        id = "author-information-section"
        h3 { +"Author Information" }

        // Author Type
        label {
            +"Author Type"
            span("required-indicator") { +"*" }
        }
        div("author-type-select") {
            radioGroup(
                "authorType",
                listOf(
                    RadioGroupOption("single", "Single Author"),
                    RadioGroupOption("team", "Team")
                )
            ) {
                required = true
                selectedOption = when(data.author) {
                    is Author.SingleAuthor -> "single"
                    is Author.Team -> "team"
                    else -> null
                }
                layout = RadioGroupLayout.HORIZONTAL
                block = {
                    attributes["hx-get"] = "/app/ideas/create/author-fields"
                    attributes["hx-target"] = "#author-fields"
                    attributes["hx-swap"] = "innerHTML"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-vals"] = "js:{authorType: event.target.value}"
                }
            }
            p("validation-error-message") {
                id = "validation-error-authorType"
            }
        }

        // Dynamic author fields container
        div {
            id = "author-fields"
            when (data.author) {
                is Author.SingleAuthor -> {
                    singleAuthorFields(data.author)
                }
                is Author.Team -> {
                    teamAuthorFields(data.author)
                }
                else -> { }
            }
        }
    }
}

fun DIV.singleAuthorFields(data: Author.SingleAuthor? = null) {
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
        value = data?.name ?: ""
        placeholder = "Your name or username"
    }
    p("validation-error-message") {
        id = "validation-error-authorName"
    }
}

fun DIV.teamAuthorFields(data: Author.Team? = null) {
    p("subtle") {
        style = "margin-bottom: var(--spacing-sm);"
        +"Add team members below. Each member should have a name, role, and contributions."
    }

    div {
        id = "team-members-container"
        classes += "stack stack--sm"

        div("team-member-row stack stack--xs") {
            data?.members?.forEachIndexed { index, author ->
                teamAuthor(author, index)
            }
            teamAuthor(null, data?.members?.size ?: 0)
        }
    }

    button(type = ButtonType.button, classes = "btn btn--sm btn--neutral") {
        style = "margin-top: var(--spacing-xs);"
        attributes["onclick"] = "addTeamMember()"
        +"+ Add Team Member"
    }
}

private fun DIV.teamAuthor(author: Author.TeamAuthor? = null, index: Int) {
    label { +"Member Name" }
    input {
        name = "teamMembers[$index][name]"
        type = InputType.text
        classes += "form-control"
        required = true
        value = author?.name ?: ""
        placeholder = "Team member name"
    }

    label { +"Role" }
    input {
        name = "teamMembers[0][role]"
        type = InputType.text
        classes += "form-control"
        value = author?.role ?: ""
        placeholder = "e.g., Lead Designer"
    }
    p("validation-error-message") {
        id = "validation-error-teamMembers[$index][role]"
    }

    label { +"Contributions" }
    input {
        name = "teamMembers[0][contributions]"
        type = InputType.text
        classes += "form-control"
        value = author?.contributions?.joinToString(", ") ?: ""
        placeholder = "e.g., Design, Testing (comma-separated)"
    }
    p("validation-error-message") {
        id = "validation-error-teamMembers[$index][contributions]"
    }
}