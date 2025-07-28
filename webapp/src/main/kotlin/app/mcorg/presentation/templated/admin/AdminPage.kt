package app.mcorg.presentation.templated.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import java.time.format.DateTimeFormatter

fun adminPage(currentUser: TokenProfile, users: List<ManagedUser>, worlds: List<ManagedWorld>) = createPage("Admin", user = currentUser) {
    classes += "admin-page"
    div("admin-header") {
        h1 {
            + "Admin Dashboard"
        }
        p("subtle") {
            + "Manage users, worlds, and system settings."
        }
    }
    section("user-management") {
        div("user-management-header") {
            h2 {
                + "User Management"
            }
            p("subtle") {
                + "View and manage all users in the system. You can make users admins, ban users, or view their details."
            }
        }
        input {
            placeholder = "Search users..."
        }
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
                    }
                }
            }
        }
    }
    section("world-management") {
        div("world-management-header") {
            h2 {
                + "World Management"
            }
            p("subtle") {
                + "View and manage all worlds in the system. You can view details, edit, or delete worlds."
            }
        }
        input {
            placeholder = "Search worlds..."
        }
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
                            neutralButton("View World")
                            neutralButton("Edit World")
                            dangerButton("Delete World")
                        }
                    }
                }
            }
        }
    }
}