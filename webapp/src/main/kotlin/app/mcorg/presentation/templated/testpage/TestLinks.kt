package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.link.LinkState
import app.mcorg.presentation.templated.common.link.LinkVariant
import app.mcorg.presentation.templated.common.link.linkComponent
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary

fun MAIN.testLinks() {
    details {
        open = true
        summary { + "Links" }
        div("link-row") {
            linkComponent(Link.Home) {
                + "Home"
            }
            linkComponent(Link.Ideas) {
                state = LinkState.DISABLED
                + "Ideas"
            }
            linkComponent(Link.Worlds.world(2)) {
                variant = LinkVariant.SUBTLE
                + "World 2"
            }
        }
    }
}