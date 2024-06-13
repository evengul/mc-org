package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.World
import app.mcorg.presentation.htmx.*

fun worldsPage(worlds: List<World>, isAdmin: List<Int> = emptyList()): String {
    return page(title = "Your worlds", id = "worlds-page") {
        form(classes = "create-form") {
            id = "add-world-container"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            label {
                htmlFor = "world-name-input"
                + "Name"
            }
            input {
                name = "world-name"
                id = "world-name-input"
                required = true
                minLength = "3"
                maxLength = "120"
            }
            button {
                + "Create world"
            }
        }
        ul(classes = "resource-list") {
            id = "worlds-list"
            for(world in worlds.sortedBy { it.name }) {
                li(classes = "resource-list-item") {
                    id = "worlds-world-${world.id}"
                    a(classes = "resource-list-item-link") {
                        id = "worlds-world-${world.id}-link"
                        href = "/worlds/${world.id}"
                        + world.name
                    }
                    if (isAdmin.contains(world.id)) {
                        button(classes = "resource-list-item-delete-button") {
                            id = "worlds-world-${world.id}-delete-button"
                            type = ButtonType.button
                            hxDelete("/worlds/${world.id}")
                            hxTarget("closest li")
                            hxSwap("outerHTML")
                            hxConfirm("Are you sure you want to delete this world? All teams and projects inside it will also be deleted.")
                            + "Delete world"
                        }
                    }
                }
            }
        }
    }
}