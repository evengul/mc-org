package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.a
import kotlinx.html.li
import kotlinx.html.ul
import no.mcorg.domain.ResourcePack

fun resourcePacksPage(packs: List<ResourcePack>): String {
    return page("Resource Packs") {
        ul {
            for (pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                }
            }
        }
    }
}
