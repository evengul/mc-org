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
            span {
                classes = setOf("icon", "icon-user")
            }
            + currentUser.username
        }
        for (user in users) {
            li {
                span {
                    classes = setOf("icon", "icon-user")
                }
                + user.username
            }
        }
    }
}

private fun getRightIcons(isAdmin: Boolean, worldId: Int) =
    if (isAdmin) listOf(NavBarRightIcon("user-add", "Add user", "/app/worlds/$worldId/users/add"))
    else emptyList()