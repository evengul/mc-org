package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.idea.IdeaSearchFilters
import app.mcorg.pipeline.idea.PaginatedResult
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.*

fun ideasPage(
    user: TokenProfile,
    result: PaginatedResult<Idea>,
    filters: IdeaSearchFilters = IdeaSearchFilters()
): String = pageShell(
    pageTitle = "MC-ORG — Ideas",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/pages/idea-hub.css",
    )
) {
    appHeader(user = user) {
        current("Ideas")
    }
    main {
        container {
            div("ideas-layout") {
                // Mobile: filter toggle button (hidden on desktop)
                button(classes = "btn btn--ghost ideas-filter-toggle") {
                    attributes["aria-controls"] = "ideas-filter-panel"
                    attributes["onclick"] =
                        "document.getElementById('ideas-filter-panel').classList.toggle('ideas-filter-panel--open'); document.getElementById('ideas-filter-backdrop').classList.toggle('ideas-filter-backdrop--open')"
                    +"Filters"
                }

                // Mobile: backdrop for filter overlay
                div("ideas-filter-backdrop") {
                    id = "ideas-filter-backdrop"
                    attributes["onclick"] =
                        "document.getElementById('ideas-filter-panel').classList.remove('ideas-filter-panel--open'); this.classList.remove('ideas-filter-backdrop--open')"
                }

                // Filter sidebar
                aside("ideas-filter-panel") {
                    id = "ideas-filter-panel"
                    // Mobile close button
                    div("ideas-filter-close") {
                        button(classes = "btn btn--ghost btn--sm") {
                            attributes["onclick"] =
                                "document.getElementById('ideas-filter-panel').classList.remove('ideas-filter-panel--open'); document.getElementById('ideas-filter-backdrop').classList.remove('ideas-filter-backdrop--open')"
                            +"Close"
                        }
                    }
                    ideaFilter()
                }

                // Card grid + pagination wrapped in #ideas-list-container
                div {
                    id = "ideas-list-container"
                    ideasListContainerContent(result, filters)
                }
            }

            // "Submit Idea" button for creators - header area
            if (user.isIdeaCreator) {
                a(classes = "btn btn--primary ideas-submit-btn") {
                    href = "${Link.Ideas.to}/create"
                    +"Submit Idea"
                }
            }
        }
    }
}
