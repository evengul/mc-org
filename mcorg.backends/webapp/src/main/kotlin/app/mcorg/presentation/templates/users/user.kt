package app.mcorg.presentation.templates.users

import app.mcorg.domain.User
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createUserListElement(worldId: Int, user: User, currentUserIsAdmin: Boolean) = createHTML().li {
    user(worldId, user, currentUserIsAdmin)
}

fun LI.user(worldId: Int, user: User, currentUserIsAdmin: Boolean) {
    id = "user-list-user-${user.id}"
    span {
        classes = setOf("icon-row")
        span {
            classes = setOf("icon", "icon-user")
        }
        h2 {
            + user.username
        }
    }
    if (currentUserIsAdmin) {
        button {
            classes = setOf("button-danger")
            hxDelete("/app/worlds/${worldId}/users/${user.id}")
            hxTarget("#user-list-user-${user.id}")
            hxSwap("outerHTML")
            + "Remove"
        }
    }
}