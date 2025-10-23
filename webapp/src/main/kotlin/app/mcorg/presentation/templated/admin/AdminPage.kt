package app.mcorg.presentation.templated.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.utils.formatAsDate
import app.mcorg.presentation.templated.utils.formatAsDateTime
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*

fun adminPage(
    currentUser: TokenProfile,
    users: List<ManagedUser>,
    worlds: List<ManagedWorld>,
    unreadNotificationCount: Int = 0
) = createPage(
    "Admin",
    user = currentUser,
    unreadNotificationCount = unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForAdminPage()
) {
    classes += "admin-page"

    adminPageHeader()
    userManagementSection(users)
    worldManagementSection(worlds)
}

private fun MAIN.adminPageHeader() {
    div("admin-header") {
        h1 {
            +"Admin Dashboard"
        }
        p("subtle") {
            +"Manage users, worlds, and system settings."
        }
    }
}

private fun MAIN.userManagementSection(users: List<ManagedUser>) {
    section("user-management") {
        userManagementHeader()
        div("search-wrapper") {
            input {
                id = "user-search-input"
                type = InputType.search
                placeholder = "Search users..."
                name = "query"
                hxGet(Link.AdminDashboard.to + "/users/search")
                hxTarget("#admin-user-rows")
                hxSwap("outerHTML")
                hxIndicator(".search-wrapper")
                hxTrigger("input changed delay:500ms")
            }
        }
        userManagementTable(users)
    }
}

private fun FlowContent.userManagementHeader() {
    div("user-management-header") {
        h2 {
            +"User Management"
        }
        p("subtle") {
            +"View and manage all users in the system. You can make users admins, ban users, or view their details."
        }
    }
}

private fun FlowContent.userManagementTable(users: List<ManagedUser>) {
    table {
        thead {
            tr {
                th {
                    +"User"
                }
                th {
                    +"Username"
                }
                th {
                    +"Email"
                }
                th {
                    +"Status"
                }
                th {
                    +"Joined"
                }
                th {
                    +"Last Active"
                }
                th {
                    +"Actions"
                }
            }
        }
        tbody {
            userRows(users)
        }
    }
}

fun TBODY.userRows(users: List<ManagedUser>) {
    id = "admin-user-rows"
    users.forEach { user ->
        tr {
            td {
                +user.displayName
            }
            td {
                +user.minecraftUsername
            }
            td {
                +user.email
            }
            td {
                +user.globalRole.toPrettyEnumName()
            }
            td {
                +user.joinedAt.formatAsDate()
            }
            td {
                +(user.lastSeen?.formatAsDateTime() ?: "Never")
            }
            td("actions") {
                userActionButtons(user)
            }
        }
    }
}

private fun TD.userActionButtons(user: ManagedUser) {
    when (user.globalRole) {
        Role.OWNER -> {}
        Role.ADMIN -> neutralButton("Remove Admin")
        Role.MEMBER -> neutralButton("Make Admin")
        Role.BANNED -> neutralButton("Unban User")
    }
    if (user.globalRole != Role.BANNED) {
        dangerButton("Ban user")
    }
    dangerButton("Delete user")
}

private fun MAIN.worldManagementSection(worlds: List<ManagedWorld>) {
    section("world-management") {
        worldManagementHeader()
        div("search-wrapper") {
            input {
                id = "world-search-input"
                type = InputType.search
                placeholder = "Search worlds..."
                name = "query"
                hxGet(Link.AdminDashboard.to + "/worlds/search")
                hxTarget("#admin-world-rows")
                hxSwap("outerHTML")
                hxIndicator(".search-wrapper")
                hxTrigger("input changed delay:500ms")
            }
        }
        worldManagementTable(worlds)
    }
}

private fun FlowContent.worldManagementHeader() {
    div("world-management-header") {
        h2 {
            +"World Management"
        }
        p("subtle") {
            +"View and manage all worlds in the system. You can view details, edit, or delete worlds."
        }
    }
}

private fun FlowContent.worldManagementTable(worlds: List<ManagedWorld>) {
    table {
        thead {
            tr {
                th {
                    +"Name"
                }
                th {
                    +"Version"
                }
                th {
                    +"Projects"
                }
                th {
                    +"Members"
                }
                th {
                    +"Created On"
                }
                th {
                    +"Actions"
                }
            }
        }
        tbody {
            worldRows(worlds)
        }
    }
}

fun TBODY.worldRows(worlds: List<ManagedWorld>) {
    id = "admin-world-rows"
    worlds.forEach { world ->
        tr {
            td {
                +world.name
            }
            td {
                +world.version.toString()
            }
            td {
                +world.projects.toString()
            }
            td {
                +world.members.toString()
            }
            td {
                +world.createdAt.formatAsDate()
            }
            td("actions") {
                worldActionButtons()
            }
        }
    }
}

private fun TD.worldActionButtons() {
    neutralButton("View World")
    neutralButton("Edit World")
    dangerButton("Delete World")
}
