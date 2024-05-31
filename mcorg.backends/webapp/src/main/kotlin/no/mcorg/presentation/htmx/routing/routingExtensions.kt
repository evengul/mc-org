package no.mcorg.presentation.htmx.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.a
import kotlinx.html.p
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.configuration.usersApi
import no.mcorg.presentation.htmx.handlers.getResourcePackParam
import no.mcorg.presentation.htmx.handlers.getWorldParam
import no.mcorg.presentation.htmx.handlers.getWorldTeamParams
import no.mcorg.presentation.htmx.templates.pages.page

fun Routing.getAuthed(path: String, permissionLevel: PermissionLevel, authority: Authority,
                      ifAuthed: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit) {
    get(path) {
        call.handleRequest(permissionLevel, authority, ifAuthed, this)
    }
}

fun Routing.postAuthed(path: String, permissionLevel: PermissionLevel, authority: Authority,
                       ifAuthed: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit) {
    post(path) {
        call.handleRequest(permissionLevel, authority, ifAuthed, this)
    }
}

fun Routing.putAuthed(path: String, permissionLevel: PermissionLevel, authority: Authority,
                       ifAuthed: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit) {
    put(path) {
        call.handleRequest(permissionLevel, authority, ifAuthed, this)
    }
}

fun Routing.deleteAuthed(path: String, permissionLevel: PermissionLevel, authority: Authority,
                       ifAuthed: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit) {
    delete(path) {
        call.handleRequest(permissionLevel, authority, ifAuthed, this)
    }
}

private suspend fun ApplicationCall.handleRequest(permissionLevel: PermissionLevel, authority: Authority,
                                                  ifAuthed: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit,
                                                  parent: PipelineContext<Unit, ApplicationCall>) {
    val userId = getUserId()
    if (userId == null || !usersApi().userExists(userId)) {
        respondRedirect("/signin")
    } else if (hasPermission(userId, permissionLevel, authority)) {
        ifAuthed(parent, Unit)
    } else {
        respondHtml(page {
            p {
                + "You are not allowed to see this content. You can either "
                a {
                    href = "/"
                    + "go home"
                }
                + " or "
                a {
                    href = "/signout"
                    + "sign out."
                }
            }
        })
    }
}

private suspend fun ApplicationCall.hasPermission(userId: Int, permissionLevel: PermissionLevel, authority: Authority): Boolean {
    when (permissionLevel) {
        PermissionLevel.AUTHENTICATED -> return true
        PermissionLevel.WORLD -> {
            val worldId = getWorldParam() ?: return false
            return permissionsApi().hasWorldPermission(userId, authority, worldId)
        }
        PermissionLevel.TEAM,
        PermissionLevel.PROJECT -> {
            val (_, teamId) = getWorldTeamParams() ?: return false
            return permissionsApi().hasTeamPermission(userId, authority, teamId)
        }
        PermissionLevel.PACK -> {
            val packId = getResourcePackParam() ?: return false
            return permissionsApi().hasPackPermission(userId, authority, packId)
        }
    }
}

