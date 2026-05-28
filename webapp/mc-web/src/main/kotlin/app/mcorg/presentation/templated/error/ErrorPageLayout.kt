package app.mcorg.presentation.templated.error

import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.header
import kotlinx.html.main
import kotlinx.html.p

fun errorPageLayout(
    pageTitle: String,
    heading: String,
    body: String,
    ctaText: String? = null,
    ctaHref: String? = null,
): String = pageShell(
    pageTitle = pageTitle,
    stylesheets = listOf("/static/styles/components/error-page.css"),
) {
    header("error-brand-bar") {
        a(classes = "error-brand-bar__logo") {
            href = "/"
            +"MC-ORG"
        }
    }
    main {
        div("error-page") {
            div("error-page__card") {
                h1("error-page__heading") { +heading }
                p("error-page__body") { +body }
                if (ctaText != null && ctaHref != null) {
                    errorPageCta(ctaText, ctaHref)
                }
            }
        }
    }
}

private fun FlowContent.errorPageCta(text: String, target: String) {
    a(classes = "btn btn--primary error-page__cta") {
        href = target
        +text
    }
}
