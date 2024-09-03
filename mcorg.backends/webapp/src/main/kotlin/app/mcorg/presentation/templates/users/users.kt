package app.mcorg.presentation.templates.users

import app.mcorg.domain.User
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun users(worldId: Int, currentUser: User, users: List<User>, isAdmin: Boolean): String = mainPageTemplate(
    selectedPage = MainPage.USERS,
    worldId = worldId,
    title = "Users",
    rightIcons = getRightIcons(isAdmin, worldId)) {
    ul {
        li {
            classes = setOf("selected")
            + currentUser.username
        }
        for (user in users) {
            li {
                + user.username
            }
        }
    }
}

private fun getRightIcons(isAdmin: Boolean, worldId: Int) =
    if (isAdmin) listOf(NavBarRightIcon("Add user", "Add user", "/app/worlds/$worldId/users/add"))
    else emptyList()