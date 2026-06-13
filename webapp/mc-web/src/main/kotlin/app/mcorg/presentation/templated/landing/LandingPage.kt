package app.mcorg.presentation.templated.landing

import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span

fun landingPage(microsoftUrl: String): String = pageShell(
    pageTitle = "Seam — Minecraft Resource Planner",
    stylesheets = listOf(
        "/static/styles/pages/landing-page.css",
    )
) {
    header("landing-brand-bar") {
        a(classes = "landing-brand-bar__logo") {
            href = "/"
            +"Seam"
        }
    }
    main {
        container {
            div("landing-page") {
                landingHero(microsoftUrl)
            }
        }
    }
}

private fun kotlinx.html.FlowContent.landingHero(microsoftUrl: String) {
    div("landing-hero") {
        h1("landing-hero__title") {
            +"Organize your Minecraft projects."
        }
        p("landing-hero__tagline") {
            +"Plan builds, track resources, and turn farms into project dependencies — without leaving the field."
        }
        a(classes = "btn-microsoft landing-hero__cta") {
            href = microsoftUrl
            attributes["aria-label"] = "Sign in with Microsoft"
            img(classes = "btn-microsoft__logo") {
                src = "/static/icons/Microsoft_Logo_48x48.svg"
                alt = ""
                attributes["aria-hidden"] = "true"
            }
            span { +"Sign in with Microsoft" }
        }
        p("landing-hero__learn-more") {
            +"New to Seam? "
            a {
                href = "https://seam.gg"
                +"See how it works →"
            }
        }
    }
}
