package app.mcorg.presentation.templates.project

import app.mcorg.domain.User
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun assignUser(users: List<User>, selected: Int?): String = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Assign user"
        }
    }
    main {
        ul {
            val selectedUser = users.find { it.id == selected }
            if (selectedUser != null) {
                li {
                    + selectedUser.username
                }
            }
            for (user in users) {
                if (user.id != selected) {
                    li {
                        + user.username
                    }
                }
            }
        }
    }
}