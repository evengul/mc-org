package app.mcorg.presentation.configuration

import app.mcorg.domain.cqrs.commands.project.AddProjectAssignmentCommand
import app.mcorg.domain.cqrs.commands.project.CreateProjectCommand
import app.mcorg.domain.cqrs.commands.project.DeleteProjectCommand
import app.mcorg.domain.cqrs.commands.project.RemoveProjectAssignmentCommand
import app.mcorg.domain.cqrs.commands.user.AddUserCommand
import app.mcorg.domain.cqrs.commands.user.RemoveUserCommand
import app.mcorg.domain.cqrs.commands.world.DeleteWorldCommand
import app.mcorg.domain.cqrs.commands.world.SelectWorldCommand
import app.mcorg.presentation.entities.project.CreateProjectRequest

object UserCommands {
    fun addToWorld(worldId: Int, username: String) = AddUserCommand(usersApi, permissionsApi).execute(AddUserCommand.CommandInput(worldId, username))
    fun removeFromWorld(worldId: Int, userId: Int) = RemoveUserCommand(usersApi, permissionsApi, projectsApi).execute(RemoveUserCommand.CommandInput(worldId, userId))
}

object WorldCommands {
    fun deleteWorld(id: Int) = DeleteWorldCommand(worldsApi, permissionsApi, usersApi).execute(DeleteWorldCommand.CommandInput(id))
    fun selectWorld(userId: Int, worldId: Int) = SelectWorldCommand(usersApi).execute(SelectWorldCommand.CommandInput(userId, worldId))
}

object ProjectCommands {
    fun createProject(worldId: Int, request: CreateProjectRequest) = CreateProjectCommand(projectsApi).execute(
        CreateProjectCommand.CommandInput(worldId, request.name, request.priority, request.dimension, request.requiresPerimeter))
    fun deleteProject(projectId: Int) = DeleteProjectCommand(projectsApi).execute(DeleteProjectCommand.CommandInput(projectId))

    fun assign(worldId: Int, projectId: Int, userId: Int) = AddProjectAssignmentCommand(permissionsApi, projectsApi).execute(AddProjectAssignmentCommand.CommandInput(worldId, projectId, userId))
    fun removeAssignment(projectId: Int) = RemoveProjectAssignmentCommand(projectsApi).execute(RemoveProjectAssignmentCommand.CommandInput(projectId))
}