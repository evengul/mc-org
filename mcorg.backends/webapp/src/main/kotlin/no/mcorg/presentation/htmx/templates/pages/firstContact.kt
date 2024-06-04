package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun firstContact(): String {
    return page {
        p(classes = "intro") {
            + "MC-ORG consists of worlds, just like your Minecraft worlds. To get started, create your first world."
        }
        form {
            id = "landingpage-create-world-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/worlds"

            span(classes = "create-world-input-pair") {
                id = "create-world-name-container"
                label {
                    id = "create-world-name-label"
                    htmlFor = "create-world-name-input"
                    + "Name"
                }
                input {
                    id = "create-world-name-input"
                    name = "world-name"
                    required = true
                    minLength = "3"
                    maxLength = "120"
                }
            }
            span(classes = "create-world-input-pair") {
                id = "create-world-is-multiplayer-container"
                label {
                    id = "create-world-is-multiplayer-label"
                    htmlFor = "create-world-is-multiplayer-input"
                    + "Multiplayer world"
                }
                input {
                    id = "create-world-is-multiplayer-input"
                    type = InputType.checkBox
                    name = "is-multiplayer"
                    hxGet("/htmx/first-world-team")
                    hxSwap("innerHTML")
                    hxTarget("#create-world-team-creator-container")
                }
            }
            span {
                id = "create-world-team-creator-container"
            }
            button {
                id = "create-world-submit"
                type = ButtonType.submit
                + "Create world"
            }
        }
    }
}

fun firstWorldTeam(): String {
    return createHTML().span {
        id = "first-world-team"
        p {
            + "Do you want to create a team for you and your friends to plan your projects in? If you don't need more than one team in this world, we'll create a simple one for you."
        }
        span(classes = "create-world-input-pair") {
            label {
                id = "create-world-team-name-label"
                htmlFor = "create-world-team-name-input"
                + "Team name"
            }
            input {
                id = "create-world-team-name-input"
                name  = "team-name"
                type = InputType.text
                required = true
                minLength = "3"
                maxLength = "120"
                value = "The cool team"
            }
        }
    }
}