package app.mcorg.presentation.templated.landing

import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.header
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

fun landingPage(microsoftUrl: String): String = pageShell(
    pageTitle = "MC-ORG — Minecraft Resource Planner",
    stylesheets = listOf(
        "/static/styles/pages/landing-page.css",
    )
) {
    header("landing-brand-bar") {
        a(classes = "landing-brand-bar__logo") {
            href = "/"
            +"MC-ORG"
        }
    }
    main {
        container {
            div("landing-page") {
                landingHero(microsoftUrl)
                landingFeatures()
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
    }
}

private fun kotlinx.html.FlowContent.landingFeatures() {
    ul("landing-features") {
        li("landing-feature") {
            h2("landing-feature__title") { +"Define projects" }
            p("landing-feature__body") {
                +"Capture each build, farm, or contraption with the resources it needs and the tasks it depends on."
            }
        }
        li("landing-feature") {
            h2("landing-feature__title") { +"Track resources" }
            p("landing-feature__body") {
                +"Increment counters one stack at a time while mining; see progress at a glance from anywhere."
            }
        }
        li("landing-feature") {
            h2("landing-feature__title") { +"Resolve dependencies" }
            p("landing-feature__body") {
                +"Generate a production path that respects what you've already built and what's still planned."
            }
        }
    }
}
