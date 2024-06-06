package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.ResourcePack
import app.mcorg.domain.Team
import app.mcorg.domain.World
import app.mcorg.presentation.htmx.*

fun worldPage(world: World, teams: List<Team>, packs: List<ResourcePack>, ownedPacks: List<ResourcePack>): String {
    return page(title = world.name, id = "world-page") {
        h2 {
            + "Teams"
        }
        form(classes = "create-form") {
            id = "world-add-team-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/worlds/${world.id}/teams"
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
        ul(classes = "resource-list") {
            id = "teams-list"
            for(team in teams) {
                li(classes = "resource-list-item") {
                    id = "team-${team.id}"
                    a(classes = "resource-list-item-link") {
                        id = "team-${team.id}-link"
                        href = "/worlds/${team.worldId}/teams/${team.id}"
                        + team.name
                    }
                    button(classes = "resource-list-item-delete-button") {
                        id = "team-${team.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${team.worldId}/teams/${team.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        hxConfirm("Are you sure you want to delete this team? All projects inside it will also be deleted.")
                        + "Delete"
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        val ownedNotAdded = ownedPacks.filter { owned -> packs.none {shared -> shared.id == owned.id } }
        val canAdd = ownedNotAdded.isNotEmpty();
        form(classes = "create-form") {
            id = "add-world-pack-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/worlds/${world.id}/resourcepacks"
            label {
                htmlFor = "add-world-pack-select"
                + "Select pack to share with this world"
            }
            if (canAdd) {
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
            } else {
                a {
                    href = "/resourcepacks"
                    + "Make a resource pack to share it with a world"
                }
            }
            button {
                id = "add-world-pack-button"
                type = ButtonType.submit
                disabled = !canAdd
                + "Add pack to world"
            }
        }
        ul(classes = "resource-list") {
            id = "world-packs"
            for(pack in packs) {
                li(classes = "resource-list-item") {
                    id = "world-pack-${pack.id}"
                    a(classes = "resource-list-item-link") {
                        id = "world-pack-${pack.id}-link"
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button(classes = "resource-list-item-delete-button") {
                        id = "world-pack-${pack.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${world.id}/resourcepacks/${pack.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        hxConfirm("Are you sure you want to remove this resource pack from this world?")
                        + "Remove resourcepack from world"
                    }
                }
            }
        }
    }
}