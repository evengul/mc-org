package app.mcorg.presentation.templates.project

import app.mcorg.domain.User
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun assignUser(backLink: String, users: List<User>, selected: Int?): String = subPageTemplate("Assign user", backLink = backLink) {
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