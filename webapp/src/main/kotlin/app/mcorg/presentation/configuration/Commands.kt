package app.mcorg.presentation.configuration

import app.mcorg.domain.cqrs.commands.user.AddUserCommand
import app.mcorg.domain.cqrs.commands.user.RemoveUserCommand

object UserCommands {
    fun addToWorld(worldId: Int, username: String) = AddUserCommand(usersApi, permissionsApi).execute(AddUserCommand.CommandInput(worldId, username))
    fun removeFromWorld(worldId: Int, userId: Int) = RemoveUserCommand(usersApi, permissionsApi, projectsApi).execute(RemoveUserCommand.CommandInput(worldId, userId))
}