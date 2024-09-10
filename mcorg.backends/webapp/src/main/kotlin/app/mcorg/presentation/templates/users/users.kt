package app.mcorg.presentation.templates.users

import app.mcorg.domain.User
import app.mcorg.presentation.hxDelete
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
        id = "users-list"
        li {
            span {
                classes = setOf("selected", "icon-row")
                span {
                    classes = setOf("icon", "icon-user")
                }
                h2 {
                    + currentUser.username
                }
            }
        }
        for (user in users) {
            li {
                span {
                    classes = setOf("icon-row")
                    span {
                        classes = setOf("icon", "icon-user")
                    }
                    h2 {
                        + user.username
                    }
                }
                if (isAdmin) {
                    button {
                        classes = setOf("button-danger")
                        hxDelete("/app/worlds/${worldId}/users/${user.id}")
                        + "Remove"
                    }
                }
            }
        }
    }
}

private fun getRightIcons(isAdmin: Boolean, worldId: Int) =
    if (isAdmin) listOf(NavBarRightIcon("user-add", "Add user", "/app/worlds/$worldId/users/add"))
    else emptyList()