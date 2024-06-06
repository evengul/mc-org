package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import app.mcorg.presentation.htmx.templates.SITE_TITLE
import app.mcorg.presentation.htmx.templates.baseTemplate
import java.time.LocalDate

fun page(siteTitle: String = "MC-ORG", title: String = "MC-ORG", content: MAIN.() -> Unit): String {
    return baseTemplate(siteTitle = siteTitle) {
        header {
            h1(classes = SITE_TITLE) {
                + title
            }
            nav {
                id = "main-navigation"
                ul {
                    li {
                        classes = setOf("nav-item")
                        a {
                            href = "/"
                            + "Home"
                        }
                    }
                    li {
                        classes = setOf("nav-item")
                        a {
                            href = "/worlds"
                            + "Worlds"
                        }
                    }
                    li {
                        classes = setOf("nav-item")
                        a {
                            href = "/resourcepacks"
                            + "Resource Packs"
                        }
                    }
                    li {
                        classes = setOf("nav-item")
                        a {
                            href = "/profile"
                            + "Profile"
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