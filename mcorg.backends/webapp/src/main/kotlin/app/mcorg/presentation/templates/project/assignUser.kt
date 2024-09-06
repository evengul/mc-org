package app.mcorg.presentation.templates.project

import app.mcorg.domain.User
import app.mcorg.presentation.*
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun assignUser(backLink: String, assignLink: String, from: String, users: List<User>, selected: Int?): String = subPageTemplate("Assign user", backLink = backLink) {
    ul {
        classes = setOf("selectable-list")
        val selectedUser = users.find { it.id == selected }
        if (selectedUser != null) {
            li {
                hxDelete("$assignLink?from=$from")
                classes = setOf("selected", "selectable", "assigned-user", "icon-row")
                span {
                    classes = setOf("icon", "icon-user")
                }
                h2 {
                    + selectedUser.username
                }
            }
        }
        for (user in users) {
            if (user.id != selected) {
                li {
                    hxPatch("$assignLink?username=${user.username}&from=${from}")
                    hxTarget("html")
                    hxSwap("outerHTML")
                    classes = setOf("selectable", "icon-row")
                    span {
                        classes = setOf("icon", "icon-user")
                    }
                    h2 {
                        + user.username
                    }
                }
            }
        }
    }
}