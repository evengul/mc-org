package no.mcorg.presentation.htmx.templates.pages.statusPages

import kotlinx.html.a
import kotlinx.html.p
import no.mcorg.presentation.htmx.templates.pages.page

fun forbiddenPage(): String {
    return page {
        p {
            + "You are not allowed to see this content. You can either "
            a {
                href = "/"
                + "go home"
            }
            + " or "
            a {
                href = "/signout"
                + "sign out."
            }
        }
    }
}