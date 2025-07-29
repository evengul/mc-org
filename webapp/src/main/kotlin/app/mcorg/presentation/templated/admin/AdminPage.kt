package app.mcorg.presentation.templated.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*
import java.time.format.DateTimeFormatter

fun adminPage(currentUser: TokenProfile, users: List<ManagedUser>, worlds: List<ManagedWorld>) = createPage("Admin", user = currentUser) {
    classes += "admin-page"

    adminPageHeader()
    userManagementSection(users)
    worldManagementSection(worlds)
}

private fun MAIN.adminPageHeader() {
    div("admin-header") {
        h1 {
            + "Admin Dashboard"
        }
        p("subtle") {
            + "Manage users, worlds, and system settings."
        }
    }
}

private fun MAIN.userManagementSection(users: List<ManagedUser>) {
    section("user-management") {
        userManagementHeader()
        input {
            placeholder = "Search users..."
        }
        userManagementTable(users)
    }
}

private fun FlowContent.userManagementHeader() {
    div("user-management-header") {
        h2 {
            + "User Management"
        }
        p("subtle") {
            + "View and manage all users in the system. You can make users admins, ban users, or view their details."
        }
    }
}

private fun FlowContent.userManagementTable(users: List<ManagedUser>) {
    table {
        thead {
            tr {
                th {
                    + "User"
                }
                th {
                    + "Username"
                }
                th {
                    + "Email"
                }
                th {
                    + "Status"
                }
                th {
                    "Joined"
                }
                th {
                    + "Last Active"
                }
                th {
                    + "Actions"
                }
            }
        }
        tbody {
            users.forEach { user ->
                tr {
                    td {
                        + user.displayName
                    }
                    td {
                        + user.minecraftUsername
                    }
                    td {
                        + user.email
                    }
                    td {
                        + user.globalRole.toPrettyEnumName()
                    }
                    td {
                        + user.joinedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    td {
                        + (user.lastSeen?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm")) ?: "Never")
                    }
                    td("actions") {
                        userActionButtons(user)
                    }
                }
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
        input {
            placeholder = "Search worlds..."
        }
        worldManagementTable(worlds)
    }
}

private fun FlowContent.worldManagementHeader() {
    div("world-management-header") {
        h2 {
            + "World Management"
        }
        p("subtle") {
            + "View and manage all worlds in the system. You can view details, edit, or delete worlds."
        }
    }
}

private fun FlowContent.worldManagementTable(worlds: List<ManagedWorld>) {
    table {
        thead {
            tr {
                th {
                    + "Name"
                }
                th {
                    + "Version"
                }
                th {
                    + "Projects"
                }
                th {
                    + "Members"
                }
                th {
                    + "Created On"
                }
                th {
                    + "Actions"
                }
            }
        }
        tbody {
            worlds.forEach { world ->
                tr {
                    td {
                        + world.name
                    }
                    td {
                        + world.version.toString()
                    }
                    td {
                        + world.projects.toString()
                    }
                    td {
                        + world.members.toString()
                    }
                    td {
                        + world.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    td("actions") {
                        worldActionButtons()
                    }
                }
            }
        }
    }
}

private fun TD.worldActionButtons() {
    neutralButton("View World")
    neutralButton("Edit World")
    dangerButton("Delete World")
}
