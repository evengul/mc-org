package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.Author
import app.mcorg.presentation.templated.dsl.RadioGroupLayout
import app.mcorg.presentation.templated.dsl.RadioGroupOption
import app.mcorg.presentation.templated.dsl.radioGroup
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

/**
 * [defaultName] pre-fills the field for a first-time author — almost always the signed-in user
 * crediting themselves, which was a whole wizard stage's worth of typing for no information
 * (MCO-310). Still editable, since a design can be someone else's.
 */
fun DIV.singleAuthorFields(data: Author.SingleAuthor? = null, defaultName: String = "") {
    label {
        htmlFor = "author-name"
        +"Author Name"
    }
    input {
        id = "author-name"
        name = "authorName"
        type = InputType.text
        classes += "form-control"
        // Deliberately NOT `required`. This field lives inside a collapsed <details> on the create
        // form, and a required control the browser cannot focus blocks submission with no visible
        // message at all — the form simply does nothing. Left empty it falls back to the signed-in
        // user, which is what the section already promises (MCO-310).
        value = data?.name ?: defaultName
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
