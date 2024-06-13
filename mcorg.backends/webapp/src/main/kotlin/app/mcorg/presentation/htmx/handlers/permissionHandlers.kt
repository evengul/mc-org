package app.mcorg.presentation.htmx.handlers

import app.mcorg.domain.Authority
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.htmx.routing.htmlBadRequest
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleAddTeamUser(worldId: Int, teamId: Int) {
    val username = (receiveMultipart().readAllParts().find { it.name == "username" } as PartData.FormItem?)?.value

    if (username == null) {
        htmlBadRequest(createHTML().p {
            id = "#add-team-user-error"
            + "Username is required"
        })
        return
    }

    val user = usersApi().getUser(username)

    if (user == null) {
        htmlBadRequest(createHTML().p {
            id = "#add-team-user-error"
            + "User does not exist"
        })
        return
    }

    if (permissionsApi().hasTeamPermission(user.id, Authority.PARTICIPANT, teamId)) {
        htmlBadRequest(createHTML().p {
            id = "#add-team-user-error"
            + "User is already in team"
        })
        return
    }

    if (!permissionsApi().hasWorldPermission(user.id, Authority.PARTICIPANT, worldId)) {
        permissionsApi().addWorldPermission(user.id, worldId, Authority.PARTICIPANT)
    }
    permissionsApi().addTeamPermission(user.id, teamId, Authority.PARTICIPANT)
}