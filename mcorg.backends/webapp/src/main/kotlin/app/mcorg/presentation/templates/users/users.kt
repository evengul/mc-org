package app.mcorg.presentation.templates.users

import app.mcorg.domain.User
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun users(worldId: Int, currentUser: User, users: List<User>, isAdmin: Boolean): String = baseTemplate {
    nav {
        button {
            + "Menu"
        }
        h1 {
            + "Users"
        }
        if (isAdmin) {
            a {
                href = "/app/worlds/$worldId/users/add"
                button {
                    + "Add"
                }
            }
        }
    }
    main {
        ul {
            li {
                + currentUser.username
            }
            for (user in users) {
                if (user.id != currentUser.id) {
                    li {
                        + currentUser.username
                    }
                }
            }
        }
    }
}