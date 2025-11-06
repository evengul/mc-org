package app.mcorg.pipeline.idea.createfragments

import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

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