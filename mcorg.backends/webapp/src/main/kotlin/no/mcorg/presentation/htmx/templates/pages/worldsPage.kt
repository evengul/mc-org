package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.World

fun worldsPage(worlds: List<World>): String {
    return page(title = "Your worlds") {
        button {
            type = ButtonType.button
            + "Create new world"
        }
        ul {
            for(world in worlds) {
                li {
                    a {
                        href = "/worlds/${world.id}"
                        + world.name
                    }
                    button {
                        type = ButtonType.button
                        + "Delete world"
                    }
                }
            }
        }
    }
}