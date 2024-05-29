package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.ResourcePack

fun resourcePacksPage(packs: List<ResourcePack>): String {
    return page("Resource Packs", "Resource Packs") {
        button {
            type = ButtonType.button
            + "Create new resource pack"
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
                        + "Delete resource pack"
                    }
                }
            }
        }
    }
}
