package app.mcorg.presentation.configuration

import app.mcorg.domain.cqrs.commands.project.AddProjectAssignmentCommand
import app.mcorg.domain.cqrs.commands.project.DeleteProjectCommand
import app.mcorg.domain.cqrs.commands.project.RemoveProjectAssignmentCommand

object ProjectCommands {
    fun deleteProject(projectId: Int) = DeleteProjectCommand(projectsApi).execute(DeleteProjectCommand.CommandInput(projectId))

    fun assign(worldId: Int, projectId: Int, userId: Int) = AddProjectAssignmentCommand(permissionsApi, projectsApi).execute(AddProjectAssignmentCommand.CommandInput(worldId, projectId, userId))
    fun removeAssignment(projectId: Int) = RemoveProjectAssignmentCommand(projectsApi).execute(RemoveProjectAssignmentCommand.CommandInput(projectId))
}