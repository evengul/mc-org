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
            for (user in users) {
                li {
                    + user.username
                }
            }
        }
    }
}