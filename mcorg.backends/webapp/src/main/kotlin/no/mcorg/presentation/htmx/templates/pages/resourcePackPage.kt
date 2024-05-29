package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.p
import no.mcorg.domain.ResourcePack

fun resourcePackPage(pack: ResourcePack): String {
    return page(pack.name) {
        p {
            + "This is a resource pack you have made"
        }
    }
}