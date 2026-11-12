package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.p

/**
 * Who answers for Seam. Mojang's usage guidelines require the product to name the person
 * responsible and a way to reach them that is not a chat or forum link (MCO-660), and the
 * privacy page repeats both (MCO-662). One place, so the two cannot disagree.
 */
object SeamOwner {
    const val NAME = "Even Gultvedt"
    const val CONTACT_EMAIL = "contact@seam.gg"
}

/** Mojang's required wording, verbatim. Not ours to soften. */
const val NOT_OFFICIAL_MINECRAFT =
    "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."

/**
 * The footer on every page, signed in or not — `pageShell` renders it after the page body, so a
 * new page cannot forget it.
 */
fun FlowContent.siteFooter() {
    footer("site-footer") {
        div("site-footer__inner") {
            p("site-footer__disclaimer") { +NOT_OFFICIAL_MINECRAFT }
            p("site-footer__meta") {
                +"Seam is made by ${SeamOwner.NAME} · "
                a(href = "mailto:${SeamOwner.CONTACT_EMAIL}") { +SeamOwner.CONTACT_EMAIL }
                +" · "
                a(href = Link.Privacy.to) { +"Privacy" }
            }
        }
    }
}
