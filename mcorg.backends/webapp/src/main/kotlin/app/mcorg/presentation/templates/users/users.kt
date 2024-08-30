package app.mcorg.presentation.templates.users

import app.mcorg.domain.User
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun users(currentUser: User, users: List<User>): String = baseTemplate {
    nav {
        button {
            + "Menu"
        }
        h1 {
            + "Users"
        }
        button {
            + "Add"
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