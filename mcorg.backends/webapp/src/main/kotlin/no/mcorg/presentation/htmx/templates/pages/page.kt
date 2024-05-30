package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.presentation.htmx.templates.baseTemplate
import java.time.LocalDate

fun page(siteTitle: String = "MC-ORG", title: String = "MC-ORG", content: MAIN.() -> Unit): String {
    return baseTemplate(siteTitle = siteTitle) {
        header {
            h1 {
                + title
            }
            nav {
                ul {
                    li {
                        a {
                            href = "/"
                            + "Home"
                        }
                    }
                    li {
                        a {
                            href = "/worlds"
                            + "Worlds"
                        }
                    }
                    li {
                        a {
                            href = "/resourcepacks"
                            + "Resource Packs"
                        }
                    }
                    li {
                        a {
                            href = "#"
                            + "Profile"
                        }
                    }
                    li {
                        a {
                            href = "/signout"
                            + "Sign out"
                        }
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