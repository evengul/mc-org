package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.*
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun teamPage(world: World, team: Team, projects: List<SlimProject>, packs: List<ResourcePack>): String {
    return page(title = "${world.name} - ${team.name}") {
        h2 {
            + "Projects"
        }
        button {
            type = ButtonType.button
            hxGet("/htmx/create-project")
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
        button {
            type = ButtonType.button
            + "Add resourcepack to team"
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
                        + "Remove resourcepack from team"
                    }
                }
            }
        }
    }
}

fun addProject(): String {
    return createHTML().form {
        encType = FormEncType.multipartFormData
        method = FormMethod.post
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