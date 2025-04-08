package app.mcorg.presentation.configuration

import app.mcorg.domain.cqrs.commands.project.AddProjectAssignmentCommand
import app.mcorg.domain.cqrs.commands.project.CreateProjectCommand
import app.mcorg.domain.cqrs.commands.project.DeleteProjectCommand
import app.mcorg.domain.cqrs.commands.project.RemoveProjectAssignmentCommand
import app.mcorg.presentation.entities.project.CreateProjectRequest

object ProjectCommands {
    fun createProject(worldId: Int, request: CreateProjectRequest) = CreateProjectCommand(projectsApi).execute(
        CreateProjectCommand.CommandInput(worldId, request.name, request.priority, request.dimension, request.requiresPerimeter))
    fun deleteProject(projectId: Int) = DeleteProjectCommand(projectsApi).execute(DeleteProjectCommand.CommandInput(projectId))

    fun assign(worldId: Int, projectId: Int, userId: Int) = AddProjectAssignmentCommand(permissionsApi, projectsApi).execute(AddProjectAssignmentCommand.CommandInput(worldId, projectId, userId))
    fun removeAssignment(projectId: Int) = RemoveProjectAssignmentCommand(projectsApi).execute(RemoveProjectAssignmentCommand.CommandInput(projectId))
}