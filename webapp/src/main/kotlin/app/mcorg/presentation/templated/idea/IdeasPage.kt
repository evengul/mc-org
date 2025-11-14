package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*

fun ideasPage(
    user: TokenProfile,
    ideas: List<Idea>,
    unreadNotifications: Int
) = createPage(
    user = user,
    unreadNotificationCount = unreadNotifications,
    breadcrumbs = BreadcrumbBuilder.buildForIdeasList(),
) {
    id = "ideas-page"
    header {
        ideasHeader(user)
    }
    section {
        id = "ideas-content"
        aside {
            ideaFilter()
        }
        if (ideas.isEmpty()) {
            div {
                id = "empty-ideas-container"
                emptyState(
                    id = "empty-ideas-state",
                    title = "No Ideas Found",
                    description = "No building ideas match your current filters. Try adjusting your search criteria or browse all ideas.",
                    icon = Icons.Notification.INFO
                )
            }
        }
        ul {
            ideaList(ideas)
        }
    }
}
