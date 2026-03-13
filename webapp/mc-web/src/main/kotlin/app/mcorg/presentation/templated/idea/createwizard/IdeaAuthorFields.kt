package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupLayout
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import kotlinx.html.ButtonType
import kotlinx.html.DIV
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

    button(type = ButtonType.button, classes = "btn btn--sm btn--ghost") {
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
        name = "teamMembers[$index][role]"
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
        name = "teamMembers[$index][contributions]"
        type = InputType.text
        classes += "form-control"
        value = author?.contributions?.joinToString(", ") ?: ""
        placeholder = "e.g., Design, Testing (comma-separated)"
    }
    p("validation-error-message") {
        id = "validation-error-teamMembers[$index][contributions]"
    }
}
