package app.mcorg.presentation.templated.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.dsl.BadgeVariant
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.badge
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.section
import app.mcorg.presentation.templated.utils.formatAsDate
import app.mcorg.presentation.templated.utils.formatAsDateTime
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.tfoot
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

private const val PAGE_SIZE = 10

enum class AdminTable {
    USERS,
    WORLDS;

    val singular: String get() = when (this) {
        USERS -> "user"
        WORLDS -> "world"
    }

    val plural: String get() = when (this) {
        USERS -> "users"
        WORLDS -> "worlds"
    }
}

fun adminPage(
    currentUser: TokenProfile,
    users: List<ManagedUser>,
    worlds: List<ManagedWorld>,
    totalUserCount: Int,
    totalWorldCount: Int,
): String = pageShell(
    pageTitle = "MC-ORG — Admin",
    user = currentUser,
    stylesheets = listOf(
        "/static/styles/pages/admin-page.css",
    )
) {
    appHeader(
        user = currentUser,
        breadcrumbBlock = {
            current("Admin")
        }
    )
    main {
        container {
            pageHeading(
                title = "Admin Dashboard",
                subtitle = "Manage users, worlds, and system settings.",
            )
            div("admin-page__sections") {
                userManagementSection(users, totalUserCount, currentPage = 1)
                worldManagementSection(worlds, totalWorldCount, currentPage = 1)
            }
        }
    }
}

private fun FlowContent.userManagementSection(
    users: List<ManagedUser>,
    totalUserCount: Int,
    currentPage: Int,
) {
    section(
        eyebrow = "USER MANAGEMENT",
        title = "Users",
        subtitle = "View and manage all users in the system.",
        tight = true,
    ) {
        input(classes = "admin-search") {
            id = "user-search-input"
            type = InputType.search
            name = "query"
            placeholder = "Search users by username..."
            hxGet(Link.AdminDashboard.to + "/users/search")
            hxTarget("#admin-user-rows")
            hxSwap("outerHTML")
            hxTrigger("input changed delay:300ms, search")
        }
        userManagementTable(users, totalUserCount, currentPage)
    }
}

private fun FlowContent.userManagementTable(
    users: List<ManagedUser>,
    totalUserCount: Int,
    currentPage: Int,
) {
    table(classes = "data-table") {
        thead {
            tr {
                th { +"User" }
                th { +"Username" }
                th { +"Email" }
                th { +"Status" }
                th { +"Joined" }
                th { +"Last Active" }
                th { +"Actions" }
            }
        }
        tbody {
            userRows(users)
        }
        tfoot {
            tr {
                td {
                    attributes["colspan"] = "7"
                    paginationInfo(totalUserCount, currentPage, AdminTable.USERS)
                }
            }
        }
    }
}

fun TBODY.userRows(users: List<ManagedUser>) {
    id = "admin-user-rows"
    if (users.isEmpty()) {
        tr("data-table__empty") {
            td {
                attributes["colspan"] = "7"
                +"No matches"
            }
        }
        return
    }
    users.forEach { user ->
        tr {
            td {
                attributes["data-label"] = "User"
                +user.displayName
            }
            td {
                attributes["data-label"] = "Username"
                +user.minecraftUsername
            }
            td {
                attributes["data-label"] = "Email"
                +user.email
            }
            td {
                attributes["data-label"] = "Status"
                userStatus(user.globalRole)
            }
            td {
                attributes["data-label"] = "Joined"
                +user.joinedAt.formatAsDate()
            }
            td {
                attributes["data-label"] = "Last Active"
                +(user.lastSeen?.formatAsDateTime() ?: "Never")
            }
            td {
                attributes["data-label"] = "Actions"
                div("data-table__actions") {
                    userActionButtons(user)
                }
            }
        }
    }
}

private fun FlowContent.userStatus(role: Role) {
    if (role == Role.BANNED) {
        badge(role.toPrettyEnumName(), BadgeVariant.DANGER)
    } else {
        +role.toPrettyEnumName()
    }
}

private fun FlowContent.userActionButtons(user: ManagedUser) {
    when (user.globalRole) {
        Role.OWNER -> {}
        Role.ADMIN -> button {
            classes = setOf("btn", "btn--secondary", "btn--sm")
            type = ButtonType.button
            +"Remove Admin"
        }
        Role.MEMBER -> button {
            classes = setOf("btn", "btn--secondary", "btn--sm")
            type = ButtonType.button
            +"Make Admin"
        }
        Role.BANNED -> button {
            classes = setOf("btn", "btn--secondary", "btn--sm")
            type = ButtonType.button
            +"Unban User"
        }
    }
    if (user.globalRole != Role.BANNED && user.globalRole != Role.OWNER) {
        button {
            classes = setOf("btn", "btn--danger", "btn--sm")
            type = ButtonType.button
            +"Ban user"
        }
    }
    if (user.globalRole != Role.OWNER) {
        button {
            classes = setOf("btn", "btn--danger", "btn--sm")
            type = ButtonType.button
            +"Delete user"
        }
    }
}

private fun FlowContent.worldManagementSection(
    worlds: List<ManagedWorld>,
    totalWorldCount: Int,
    currentPage: Int,
) {
    section(
        eyebrow = "WORLD MANAGEMENT",
        title = "Worlds",
        subtitle = "View and manage all worlds in the system.",
        tight = true,
    ) {
        input(classes = "admin-search") {
            id = "world-search-input"
            type = InputType.search
            name = "query"
            placeholder = "Search worlds by name..."
            hxGet(Link.AdminDashboard.to + "/worlds/search")
            hxTarget("#admin-world-rows")
            hxSwap("outerHTML")
            hxTrigger("input changed delay:300ms, search")
        }
        worldManagementTable(worlds, totalWorldCount, currentPage)
    }
}

private fun FlowContent.worldManagementTable(
    worlds: List<ManagedWorld>,
    totalWorldCount: Int,
    currentPage: Int,
) {
    table(classes = "data-table") {
        thead {
            tr {
                th { +"Name" }
                th { +"Version" }
                th { +"Projects" }
                th { +"Members" }
                th { +"Created On" }
                th { +"Actions" }
            }
        }
        tbody {
            worldRows(worlds)
        }
        tfoot {
            tr {
                td {
                    attributes["colspan"] = "6"
                    paginationInfo(totalWorldCount, currentPage, AdminTable.WORLDS)
                }
            }
        }
    }
}

fun TBODY.worldRows(worlds: List<ManagedWorld>) {
    id = "admin-world-rows"
    if (worlds.isEmpty()) {
        tr("data-table__empty") {
            td {
                attributes["colspan"] = "6"
                +"No matches"
            }
        }
        return
    }
    worlds.forEach { world ->
        tr {
            id = "world-row-${world.id}"
            td {
                attributes["data-label"] = "Name"
                +world.name
            }
            td {
                attributes["data-label"] = "Version"
                +world.version.toString()
            }
            td {
                attributes["data-label"] = "Projects"
                +world.projects.toString()
            }
            td {
                attributes["data-label"] = "Members"
                +world.members.toString()
            }
            td {
                attributes["data-label"] = "Created On"
                +world.createdAt.formatAsDate()
            }
            td {
                attributes["data-label"] = "Actions"
                div("data-table__actions") {
                    worldActionButtons(world)
                }
            }
        }
    }
}

private fun FlowContent.worldActionButtons(world: ManagedWorld) {
    a(classes = "btn btn--secondary btn--sm") {
        href = Link.Worlds.world(world.id).to
        +"View"
    }
    button {
        classes = setOf("btn", "btn--danger", "btn--sm")
        type = ButtonType.button
        hxDeleteWithConfirm(
            url = Link.Worlds.world(world.id).settings().to,
            title = "Delete World",
            description = "Are you sure you want to delete this world?",
            warning = "All related projects and tasks will also be deleted.",
            confirmText = world.name,
        )
        hxTarget("#world-row-${world.id}")
        hxSwap("delete")
        +"Delete"
    }
}

fun FlowContent.paginationInfo(
    totalCount: Int,
    currentPage: Int,
    table: AdminTable,
) {
    div("pagination-info") {
        id = "pagination-info-${table.plural}"
        paginationInfoBody(totalCount, currentPage, table)
    }
}

fun FlowContent.paginationInfoBody(
    totalCount: Int,
    currentPage: Int,
    table: AdminTable,
) {
    if (totalCount == 0) {
        span("pagination-info__label") { +"No ${table.plural} found" }
        return
    }

    val pages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE
    val start = (currentPage - 1) * PAGE_SIZE + 1
    val end = minOf(currentPage * PAGE_SIZE, totalCount)

    button {
        classes = setOf("btn", "btn--ghost", "btn--sm")
        type = ButtonType.button
        disabled = currentPage <= 1
        hxGet(Link.AdminDashboard.to + "/${table.plural}/search?page=${currentPage - 1}")
        hxInclude("#${table.singular}-search-input")
        hxTarget("#admin-${table.singular}-rows")
        hxSwap("outerHTML")
        +"Previous"
    }
    span("pagination-info__label") {
        +"Showing $start–$end of $totalCount ${table.plural} (Page $currentPage of $pages)"
    }
    button {
        classes = setOf("btn", "btn--ghost", "btn--sm")
        type = ButtonType.button
        disabled = currentPage >= pages
        hxGet(Link.AdminDashboard.to + "/${table.plural}/search?page=${currentPage + 1}")
        hxInclude("#${table.singular}-search-input")
        hxTarget("#admin-${table.singular}-rows")
        hxSwap("outerHTML")
        +"Next"
    }
}
