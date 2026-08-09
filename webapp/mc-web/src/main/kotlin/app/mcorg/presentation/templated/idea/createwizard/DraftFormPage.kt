package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.draft.name
import app.mcorg.pipeline.idea.draft.toMessage
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormEncType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.summary

/**
 * The single-page create form (MCO-310).
 *
 * Replaces a six-stage wizard in which three stages were pass-throughs: Author was one field that
 * we can usually fill in ourselves, Version was one pre-defaulted dropdown, and Items validated
 * nothing at all. Only name, description, difficulty and category are actually required, so those
 * are the only things visible up front — everything else is optional detail behind a disclosure.
 *
 * Submitting produces a *private* idea (MCO-291), which is why nothing here says "publish".
 */
fun draftFormPage(
    user: TokenProfile,
    draft: IdeaDraft,
    supportedVersions: List<MinecraftVersion.Release>,
    errors: List<ValidationFailure> = emptyList(),
): String = pageShell(
    pageTitle = "Seam — New Idea",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/form.css",
        "/static/styles/components/item-search.css",
        "/static/styles/pages/idea-wizard.css",
    )
) {
    appHeader(
        user = user,
        breadcrumbBlock = {
            link("Ideas", "/ideas").link("Drafts", "/ideas/create").current(draft.name ?: "New idea")
        }
    )
    main {
        container {
            div("idea-form-head") {
                h1("idea-form__title") { +(draft.name ?: "New idea") }
                p("idea-form__subtitle") {
                    +"Name, description and category are all you need. Everything else is optional, and it stays private until you publish it to the hub."
                }
            }
            div {
                id = FORM_ID
                draftFormContent(draft, supportedVersions, errors, user.minecraftUsername)
            }
        }
    }
}

/** The form on its own, for re-rendering in place when validation fails. */
fun draftFormFragment(
    draft: IdeaDraft,
    supportedVersions: List<MinecraftVersion.Release>,
    errors: List<ValidationFailure>,
    defaultAuthorName: String,
): String = createHTML().div {
    id = FORM_ID
    draftFormContent(draft, supportedVersions, errors, defaultAuthorName)
}

private const val FORM_ID = "idea-form"

private fun FlowContent.draftFormContent(
    draft: IdeaDraft,
    supportedVersions: List<MinecraftVersion.Release>,
    errors: List<ValidationFailure>,
    defaultAuthorName: String,
) {
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxPost("/ideas/drafts/${draft.id}/publish")
        hxTarget("#$FORM_ID")
        hxSwap("outerHTML")

        if (errors.isNotEmpty()) {
            div("callout callout--error") {
                span("callout__icon") { +"⚠" }
                div("callout__body") {
                    errors.forEach { error -> p { +error.toMessage() } }
                }
            }
        }

        div("idea-form__required") {
            draftBasicInfoFields(draft)
            draftCategorySelect(draft)
        }

        optionalSection("Design details", "Size, production rate, and anything else measurable") {
            draftCategorySchemaFields(draft)
        }

        optionalSection("Materials", "What it takes to build — or drop in a .litematic") {
            draftItemRequirementFields(draft)
        }

        optionalSection("Versions and credit", "Defaults to all versions, credited to you") {
            draftVersionFields(draft, supportedVersions)
            draftAuthorFields(draft, defaultAuthorName)
        }

        div("idea-form__actions") {
            button(classes = "btn btn--primary") {
                type = ButtonType.submit
                +"Save Idea"
            }
            // Posts the same fields to a route that skips validation, so "later" actually keeps
            // what you typed instead of quietly dropping it on the way out.
            button(classes = "btn btn--ghost") {
                type = ButtonType.button
                hxPost("/ideas/drafts/${draft.id}/save")
                +"Save for later"
            }
        }
    }
}

/**
 * A collapsed `<details>` block. Native disclosure rather than scripted accordions: it keeps the
 * fields in the form (so they still post) and stays keyboard- and search-accessible for free.
 */
private fun FlowContent.optionalSection(
    title: String,
    hint: String,
    block: FlowContent.() -> Unit,
) {
    details("idea-form__section") {
        summary("idea-form__section-summary") {
            h2("idea-form__section-title") { +title }
            span("idea-form__section-hint") { +hint }
        }
        div("idea-form__section-body") { block() }
    }
}
