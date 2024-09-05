package app.mcorg.presentation.templates.project

import app.mcorg.domain.User
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun assignUser(backLink: String, assignLink: String, users: List<User>, selected: Int?): String = subPageTemplate("Assign user", backLink = backLink) {
    ul {
        classes = setOf("selectable-list")
        val selectedUser = users.find { it.id == selected }
        if (selectedUser != null) {
            li {
                classes = setOf("selected", "icon-row")
                span {
                    classes = setOf("icon", "icon-user")
                }
                + selectedUser.username
            }
        }
        for (user in users) {
            if (user.id != selected) {
                li {
                    hxPatch("$assignLink?username=${user.username}")
                    hxTarget("html")
                    hxSwap("outerHTML")
                    classes = setOf("selectable", "icon-row")
                    span {
                        classes = setOf("icon", "icon-user")
                    }
                    + user.username
                }
            }
        }
    }
}