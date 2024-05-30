package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.ResourcePack
import no.mcorg.domain.Team
import no.mcorg.domain.World
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun worldPage(world: World, teams: List<Team>, packs: List<ResourcePack>): String {
    return page(title = world.name) {
        h2 {
            + "Teams"
        }
        button {
            type = ButtonType.button
            hxGet("/htmx/create-team")
            hxTarget("#add-team-container")
            + "Create new team in world"
        }
        div {
            id = "add-team-container"
        }
        ul {
            for(team in teams) {
                li {
                    a {
                        href = "/worlds/${team.worldId}/teams/${team.id}"
                        + team.name
                    }
                    button {
                        type = ButtonType.button
                        hxDelete("/worlds/${team.worldId}/teams/${team.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete"
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        button {
            type = ButtonType.button
            + "Add resource pack to world"
        }
        ul {
            for(pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        type = ButtonType.button
                        + "Remove resourcepack from world"
                    }
                }
            }
        }
    }
}

fun addTeam(): String {
    return createHTML().form {
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        label {
            htmlFor = "team-name-input"
            + "Name"
        }
        input {
            name = "team-name"
            required = true
            minLength = "3"
            maxLength = "120"
            id = "team-name-input"
        }
        button {
            type = ButtonType.submit
            + "Create Team"
        }
    }
}