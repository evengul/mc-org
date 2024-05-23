package no.skyteruta.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.skyteruta.templates.hxGet
import no.skyteruta.templates.hxSwap
import no.skyteruta.templates.hxTarget

fun landingPage(): String {
    return page {
        p(classes = "intro") {
            + "MC-ORG consists of worlds, just like your Minecraft worlds. To get started, create your first world."
        }
        form {
            id = "landingpage-create-world"
            encType = FormEncType.multipartFormData
            method = FormMethod.post

            span(classes = "create-world-input-pair") {
                id = "create-world-name-container"
                label {
                    id = "create-world-name-label"
                    htmlFor = "create-world-name-input"
                    + "Name"
                }
                input {
                    id = "create-world-name-input"
                    required = true
                    minLength = "3"
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
                    hxGet("/first-world-team")
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
                type = InputType.text
                required = true
                minLength = "3"
                value = "The cool team"
            }
        }
    }
}

fun firstWorldCreated(): String {
    return page {
        p {
            + "Congratulations! You just created your first world. What do you want to do with it?"
        }
        ul {
            li {
                a {
                    href = "#"
                    + "Create a collection pack possibly containing resource packs, mods/mod packs and data packs?"
                }
            }
            li {
                a {
                    href = "#"
                    + "Create a project where you gather resources and build cool stuff?"
                }
            }
        }
    }
}