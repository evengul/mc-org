package app.mcorg.presentation.templates

import app.mcorg.domain.User
import app.mcorg.domain.sortUsersBySelectedOrName
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTrigger
import kotlinx.html.SELECT
import kotlinx.html.classes
import kotlinx.html.option

fun SELECT.assign(worldUsers: List<User>, currentUser: User, assignee: User?) {
    classes = setOf("assign-select")
    name = "userId"
    hxSwap("outerHTML")
    hxTrigger("change changed")
    val noUser = User(-1, "NONE")
    val users: List<User> = if (assignee == null) listOf(noUser) + sortUsersBySelectedOrName(worldUsers, currentUser, null) else {
        val sorted = sortUsersBySelectedOrName(worldUsers, currentUser, assignee)
        (listOf(sorted[0]) + noUser + sorted.filter { it.id != sorted[0].id })
    }
    users.forEach {
        option {
            val isSelected = (assignee == null && it.id == -1) || it.id == assignee?.id
            selected = isSelected
            value = it.id.toString()
            if (isSelected) {
                if (it.username == "NONE") {
                    + "Unassigned"
                } else {
                    + "Assigned: ${it.username}"
                }
            } else {
                + (it.username.takeIf { it != "NONE" } ?: "Unassigned")
            }
        }
    }
}