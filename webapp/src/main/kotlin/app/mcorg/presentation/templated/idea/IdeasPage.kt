package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.aside
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.section

fun ideasPage(
    user: TokenProfile,
    ideas: List<Idea>,
    unreadNotifications: Int
) = createPage(
    user = user,
    pageTitle = "Ideas",
    unreadNotificationCount = unreadNotifications
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
        div {
            ideaListContainer(ideas)
        }
    }
}
