package app.mcorg.presentation.templated.link

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.p

/**
 * The device-link approval page (MCO-236). A signed-in user enters the short user code shown by the
 * Seam Companion mod; submitting binds the pending device code to their account. Rendered on both
 * GET (the form) and POST (the form plus a success/error message).
 */
fun linkPage(
    user: TokenProfile,
    prefillCode: String? = null,
    error: String? = null,
    success: String? = null,
): String = pageShell(
    pageTitle = "Seam — Link a device",
    user = user,
    // pageShell ships btn.css by default but not form.css — without this the .form-control and
    // .form-error classes below are dead and the form renders as unstyled inline browser defaults.
    stylesheets = listOf("/static/styles/components/form.css"),
) {
    appHeader(
        user = user,
        breadcrumbBlock = { current("Link a device") },
    )
    main {
        container {
            pageHeading(
                title = "Link a device",
                subtitle = "Enter the code shown in your Seam Companion mod to connect it to your account.",
            )
            section(card = true) {
                if (success != null) {
                    p("section__subtitle") { id = "link-success"; +success }
                }
                if (error != null) {
                    p("form-error") { id = "link-error"; +error }
                }
                linkForm(prefillCode)
            }
        }
    }
}

private fun FlowContent.linkForm(prefillCode: String?) {
    // .stack gives the field and the action a column layout with real spacing; without it the
    // label, input and button run together inline.
    form(classes = "stack") {
        method = kotlinx.html.FormMethod.post
        action = "/link"
        div {
            label(classes = "form-label") {
                htmlFor = "user_code"
                +"Device code"
            }
            input(classes = "form-control") {
                id = "user_code"
                name = "user_code"
                type = InputType.text
                required = true
                autoComplete = "off"
                placeholder = "ABCD-EFGH"
                value = prefillCode ?: ""
            }
        }
        // Wrapped so the button sizes to its content — a direct flex child would stretch full width.
        div {
            button(classes = "btn btn--primary", type = ButtonType.submit) {
                +"Link device"
            }
        }
    }
}
