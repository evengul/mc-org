package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.*
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.htmx.*

fun teamPage(world: World,
             team: Team,
             projects: List<SlimProject>,
             packs: List<ResourcePack>,
             ownedPacks: List<ResourcePack>,
             users: List<User>,
             isAdmin: Boolean = false): String {
    return page(title = "${world.name} - ${team.name}", id = "team-page") {
        script {
            src = "/static/response-targets.js"
            defer = true
        }
        h2 {
            + "Projects"
        }
        if (isAdmin) {
            form(classes = "create-form") {
                id = "team-add-project-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${team.worldId}/teams/${team.id}/projects"
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
        ul(classes = "resource-list") {
            for (project in projects) {
                li(classes = "resource-list-item") {
                    a(classes = "resource-list-item-link") {
                        id = "project-${project.id}-link"
                        href = "/worlds/${world.id}/teams/${team.id}/projects/${project.id}"
                        + project.name
                    }
                    if (isAdmin) {
                        button(classes = "resource-list-item-delete-button") {
                            id = "project-${project.id}-delete-button"
                            type = ButtonType.button
                            hxDelete("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}")
                            hxTarget("closest li")
                            hxSwap("outerHTML")
                            hxConfirm("Are you sure you want to delete this project?")
                            + "Delete project"
                        }
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        val ownedNotAdded = ownedPacks.filter { owned -> packs.none {shared -> shared.id == owned.id } }
        val canAdd = ownedNotAdded.isNotEmpty();
        if (isAdmin) {
            form(classes = "create-form") {
                id = "add-team-pack-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${team.worldId}/teams/${team.id}/resourcepacks"
                label {
                    htmlFor = "add-team-pack-select"
                    + "Select pack to share with this team"
                }
                if (canAdd) {
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
                } else {
                    a {
                        href = "/resourcepacks"
                        + "Make a resource pack to share it with a team"
                    }
                }
                button {
                    id = "add-team-pack-button"
                    type = ButtonType.submit
                    disabled = !canAdd
                    + "Add pack to team"
                }
            }
        }
        ul(classes = "resource-list") {
            id = "team-packs"
            for (pack in packs) {
                li(classes = "resource-list-item") {
                    id = "team-pack-${pack.id}"
                    a(classes = "resource-list-item-link") {
                        id = "team-pack-${pack.id}-link"
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    if (isAdmin) {
                        button(classes = "resource-list-item-delete-button") {
                            id = "team-pack-${pack.id}-delete-button"
                            type = ButtonType.button
                            hxDelete("/worlds/${team.worldId}/teams/${team.id}/resourcepacks/${pack.id}")
                            hxTarget("closest li")
                            hxSwap("outerHTML")
                            hxConfirm("Are you sure you want to remove this resource pack from this team?")
                            + "Remove resourcepack from team"
                        }
                    }
                }
            }
        }
        h2 {
            + "Users"
        }
        if (isAdmin) {
            form {
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                hxExtension("response-targets")
                hxPost("/worlds/${team.worldId}/teams/${team.id}/users")
                hxTargetError("#add-team-user-error")
                label {
                    htmlFor = "username"
                }
                input {
                    id = "username"
                    name = "username"
                }
                p(classes = "text-error") {
                    id = "add-team-user-error"
                }
                button {
                    + "Add user to team"
                }
            }
        }
        ul(classes = "resource-list") {
            for (user in users) {
                li(classes = "resource-list-item") {
                    + user.username
                    if (isAdmin) {
                        button(classes = "resource-list-item-delete-button") {
                            id = "team-user-${user.id}-delete-button"
                            type = ButtonType.button
                            hxDelete("/worlds/${team.worldId}/teams/${team.id}/users/${user.id}")
                            hxTarget("closest li")
                            hxSwap("outerHTML")
                            hxConfirm("Are you sure you want to remove this user from this team?")
                            + "Remove user from team"
                        }
                    }
                }
            }
        }
    }
}