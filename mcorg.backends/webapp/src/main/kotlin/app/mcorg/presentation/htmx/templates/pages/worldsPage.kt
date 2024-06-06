package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.World
import app.mcorg.presentation.htmx.hxDelete
import app.mcorg.presentation.htmx.hxGet
import app.mcorg.presentation.htmx.hxSwap
import app.mcorg.presentation.htmx.hxTarget

fun worldsPage(worlds: List<World>): String {
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
                    button(classes = "resource-list-item-delete-button") {
                        id = "worlds-world-${world.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/worlds/${world.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete world"
                    }
                }
            }
        }
    }
}