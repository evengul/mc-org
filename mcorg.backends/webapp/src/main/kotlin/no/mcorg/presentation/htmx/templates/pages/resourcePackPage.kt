package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.ResourcePack

fun resourcePackPage(pack: ResourcePack): String {
    return page(pack.name) {
        p {
            + "This is a resource pack you have made"
        }
        button {
            type = ButtonType.button
            + "Add resource to resource pack"
        }
        ul {
            for (resource in pack.resources.sortedBy { it.type }) {
                li {
                    + "[${resource.type}] ${resource.name}"
                    button {
                        type = ButtonType.button
                        + "Delete"
                    }
                }
            }
        }
    }
}