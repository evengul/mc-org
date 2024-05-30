package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.World
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun worldsPage(worlds: List<World>): String {
    return page(title = "Your worlds") {
        button {
            type = ButtonType.button
            hxGet("/htmx/create-world")
            hxTarget("#add-world-container")
            + "Create new world"
        }
        div {
            id = "add-world-container"
        }
        ul {
            for(world in worlds.sortedBy { it.name }) {
                li {
                    a {
                        href = "/worlds/${world.id}"
                        + world.name
                    }
                    button {
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

fun addWorld(): String {
    return createHTML().form {
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
}