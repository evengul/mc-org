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

fun worldPage(world: World, teams: List<Team>, packs: List<ResourcePack>, ownedPacks: List<ResourcePack>): String {
    return page(title = world.name) {
        h2 {
            + "Teams"
        }
        button {
            id = "world-add-team-show-form-button"
            type = ButtonType.button
            hxGet("/htmx/worlds/${world.id}/teams/add")
            hxTarget("#add-team-container")
            + "Create new team in world"
        }
        div {
            id = "add-team-container"
        }
        ul {
            id = "teams-list"
            for(team in teams) {
                li {
                    id = "team-${team.id}"
                    a {
                        id = "team-${team.id}-link"
                        href = "/worlds/${team.worldId}/teams/${team.id}"
                        + team.name
                    }
                    button {
                        id = "team-${team.id}-delete-button"
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
        val ownedNotAdded = ownedPacks.filter { owned -> packs.none {shared -> shared.id == owned.id } }
        if (ownedNotAdded.isNotEmpty()) {
            form {
                id = "add-world-pack-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${world.id}/resourcepacks"
                label {
                    htmlFor = "add-world-pack-select"
                    + "Select pack to share with this world"
                }
                select {
                    id = "add-world-pack-select"
                    name = "world-resource-pack-id"
                    option {
                        id = "add-world-pack-option-NONE"
                        value = ""
                        + ""
                    }
                    for (pack in ownedNotAdded) {
                        option {
                            id = "add-world-pack-option-${pack.id}"
                            value = pack.id.toString()
                            + pack.name
                        }
                    }
                }
                button {
                    id = "add-world-pack-button"
                    type = ButtonType.submit
                    + "Add pack to world"
                }
            }
        }
        ul {
            id = "world-packs"
            for(pack in packs) {
                li {
                    id = "world-pack-${pack.id}"
                    a {
                        id = "world-pack-${pack.id}-link"
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        id = "world-pack-${pack.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${world.id}/resourcepacks/${pack.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Remove resourcepack from world"
                    }
                }
            }
        }
    }
}

fun addTeam(worldId: Int): String {
    return createHTML().form {
        id = "world-add-team-form"
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        action = "/worlds/$worldId/teams"
        label {
            htmlFor = "team-name-input"
            + "Name"
        }
        input {
            id = "team-name-input"
            name = "team-name"
            required = true
            minLength = "3"
            maxLength = "120"
        }
        button {
            id = "world-add-team-submit"
            type = ButtonType.submit
            + "Create Team"
        }
    }
}