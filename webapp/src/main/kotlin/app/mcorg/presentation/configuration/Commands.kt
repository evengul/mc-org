package app.mcorg.presentation.configuration

import app.mcorg.domain.cqrs.commands.user.AddUserCommand
import app.mcorg.domain.cqrs.commands.user.RemoveUserCommand
import app.mcorg.domain.cqrs.commands.world.CreateWorldCommand
import app.mcorg.domain.cqrs.commands.world.DeleteWorldCommand
import app.mcorg.domain.cqrs.commands.world.SelectWorldCommand

object UserCommands {
    fun addToWorld(worldId: Int, username: String) = AddUserCommand(usersApi, permissionsApi).execute(AddUserCommand.CommandInput(worldId, username))
    fun removeFromWorld(worldId: Int, userId: Int) = RemoveUserCommand(usersApi, permissionsApi, projectsApi).execute(RemoveUserCommand.CommandInput(worldId, userId))
}

object WorldCommands {
    fun createWorld(userId: Int, name: String) = CreateWorldCommand(worldsApi, permissionsApi, usersApi).execute(CreateWorldCommand.CommandInput(userId, name))
    fun deleteWorld(id: Int) = DeleteWorldCommand(worldsApi, permissionsApi, usersApi).execute(DeleteWorldCommand.CommandInput(id))
    fun selectWorld(userId: Int, worldId: Int) = SelectWorldCommand(worldsApi, usersApi).execute(SelectWorldCommand.CommandInput(userId, worldId))
}