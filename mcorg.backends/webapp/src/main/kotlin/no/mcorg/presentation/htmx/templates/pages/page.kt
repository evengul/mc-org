package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.presentation.htmx.templates.baseTemplate
import java.time.LocalDate

fun page(siteTitle: String = "MC-ORG", title: String = "MC-ORG", content: MAIN.() -> Unit): String {
    return baseTemplate(siteTitle = siteTitle) {
        nav {
            h1 {
                + title
            }
            ul {
                li {
                    a {
                        href = "#"
                        + "Home"
                    }
                }
                li {
                    a {
                        href = "#"
                        + "Worlds"
                    }
                }
                li {
                    a {
                        href = "#"
                        + "Teams"
                    }
                }
                li {
                    a {
                        href = "#"
                        + "Projects"
                    }
                }
                li {
                    a {
                        href = "#"
                        + "Packs"
                    }
                }
                li {
                    a {
                        href = "#"
                        + "Profile"
                    }
                }
            }
        }
        main {
            content()
        }
        footer {
            val year = LocalDate.now().year
            + "© Even Gultvedt $year"
        }
    }
}