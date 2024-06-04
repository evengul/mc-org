package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.*
import app.mcorg.presentation.htmx.templates.hxDelete
import app.mcorg.presentation.htmx.templates.hxGet
import app.mcorg.presentation.htmx.templates.hxSwap
import app.mcorg.presentation.htmx.templates.hxTarget

fun teamPage(world: World, team: Team, projects: List<SlimProject>, packs: List<ResourcePack>, ownedPacks: List<ResourcePack>): String {
    return page(title = "${world.name} - ${team.name}") {
        h2 {
            + "Projects"
        }
        button {
            id = "team-add-project-button"
            type = ButtonType.button
            hxGet("/htmx/world/${world.id}/teams/${team.id}/projects/add")
            hxTarget("#add-project-container")
            + "Create new project in team"
        }
        div {
            id = "add-project-container"
        }
        ul {
            for (project in projects) {
                li {
                    classes = setOf("team-project-item")
                    a {
                        id = "project-${project.id}-link"
                        href = "/worlds/${world.id}/teams/${team.id}/projects/${project.id}"
                        + project.name
                    }
                    button {
                        id = "project-${project.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete project"
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
                id = "add-team-pack-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${team.worldId}/teams/${team.id}/resourcepacks"
                label {
                    htmlFor = "add-team-pack-select"
                    + "Select pack to share with this team"
                }
                select {
                    id = "add-team-pack-select"
                    name = "team-resource-pack-id"
                    option {
                        id = "add-team-pack-option-NONE"
                        value = ""
                        + ""
                    }
                    for (pack in ownedNotAdded) {
                        option {
                            id = "add-team-pack-option-${pack.id}"
                            value = pack.id.toString()
                            + pack.name
                        }
                    }
                }
                button {
                    id = "add-team-pack-button"
                    type = ButtonType.submit
                    + "Add pack to team"
                }
            }
        }
        ul {
            id = "team-packs"
            for (pack in packs) {
                li {
                    id = "team-pack-${pack.id}"
                    a {
                        id = "team-pack-${pack.id}-link"
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        id = "team-pack-${pack.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${team.worldId}/teams/${team.id}/resourcepacks/${pack.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Remove resourcepack from team"
                    }
                }
            }
        }
    }
}

fun addProject(worldId: Int, teamId: Int): String {
    return createHTML().form {
        id = "team-add-project-form"
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        action = "/worlds/$worldId/teams/$teamId/projects"
        label {
            htmlFor = "project-name-input"
            + "Name"
        }
        input {
            id = "project-name-input"
            name = "project-name"
            required = true
            minLength = "3"
            maxLength = "120"
        }
        button {
            id = "team-add-project-submit"
            type = ButtonType.submit
            + "Create new project"
        }
    }
}