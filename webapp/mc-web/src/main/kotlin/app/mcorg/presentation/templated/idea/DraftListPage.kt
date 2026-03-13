package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.idea.draft.name
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun draftListPage(
    user: TokenProfile,
    drafts: List<IdeaDraft>
): String = pageShell(
    pageTitle = "MC-ORG — My Drafts",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/project-card.css",
        "/static/styles/pages/draft-list.css",
    )
) {
    appHeader(
        user = user,
        breadcrumbBlock = {
            link("Ideas", "/ideas").current("My Drafts")
        }
    )
    main {
        container {
            div("drafts-header") {
                h1("section-heading") { +"My Drafts" }
                a(classes = "btn btn--primary") {
                    href = "#"
                    attributes["hx-post"] = "/ideas/create"
                    attributes["hx-swap"] = "none"
                    +"New Draft"
                }
            }

            div {
                id = "draft-list"
                if (drafts.isEmpty()) {
                    div("empty-state") {
                        p("empty-state__body") { +"You have no drafts. Start a new draft to submit an idea." }
                    }
                } else {
                    drafts.forEach { draft ->
                        draftCard(draft)
                    }
                }
            }
        }
    }
}

fun kotlinx.html.FlowContent.draftCard(draft: IdeaDraft) {
    div("project-card") {
        id = "draft-${draft.id}"
        div("project-card__header") {
            div {
                h2("project-card__name") {
                    +(draft.name ?: "Untitled Draft")
                }
                p("project-card__meta") {
                    span { +"Stage: ${draft.currentStage.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}" }
                    span { +" · " }
                    span { +"Updated ${draft.updatedAt.format(DATE_FMT)}" }
                }
            }
        }
        div("project-card__actions") {
            a(classes = "btn btn--secondary btn--sm") {
                href = "/ideas/drafts/${draft.id}/edit"
                +"Continue Editing"
            }
            if (draft.sourceIdeaId != null) {
                button(classes = "btn btn--ghost btn--sm") {
                    type = ButtonType.button
                    attributes["hx-delete"] = "/ideas/drafts/${draft.id}"
                    hxTarget("#draft-${draft.id}")
                    hxSwap("outerHTML")
                    attributes["hx-confirm"] = "Cancel editing? The idea will be restored and made visible again. Your changes will be lost."
                    +"Cancel editing"
                }
            } else {
                button(classes = "btn btn--danger btn--sm") {
                    type = ButtonType.button
                    attributes["hx-delete"] = "/ideas/drafts/${draft.id}"
                    hxTarget("#draft-${draft.id}")
                    hxSwap("outerHTML")
                    attributes["hx-confirm"] = "Discard this draft? This cannot be undone."
                    +"Discard"
                }
            }
        }
    }
}
