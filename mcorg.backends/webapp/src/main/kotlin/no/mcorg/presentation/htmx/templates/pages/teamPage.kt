package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.*
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun teamPage(world: World, team: Team, projects: List<SlimProject>, packs: List<ResourcePack>, ownedPacks: List<ResourcePack>): String {
    return page(title = "${world.name} - ${team.name}") {
        h2 {
            + "Projects"
        }
        button {
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
                    a {
                        href = "/worlds/${world.id}/teams/${team.id}/projects/${project.id}"
                        + project.name
                    }
                    button {
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
                        value = ""
                        + ""
                    }
                    for (pack in ownedNotAdded) {
                        option {
                            value = pack.id.toString()
                            + pack.name
                        }
                    }
                }
                button {
                    type = ButtonType.submit
                    + "Add pack to team"
                }
            }
        }
        ul {
            for (pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
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
            type = ButtonType.submit
            + "Create new project"
        }
    }
}