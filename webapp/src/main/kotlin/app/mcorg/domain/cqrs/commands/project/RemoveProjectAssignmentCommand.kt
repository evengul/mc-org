package app.mcorg.domain.cqrs.commands.project

import app.mcorg.domain.api.Projects
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class RemoveProjectAssignmentCommand(
    private val projects: Projects
) : Command<RemoveProjectAssignmentCommand.CommandInput, Success, RemoveProjectAssignmentCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, Success> {
        val (projectId) = input

        val project = projects.getProject(projectId) ?: return Output.failure(ProjectNotFound(projectId))

        if (project.assignee == null) {
            return Output.failure(ProjectDoesNotHaveAssignedUser(projectId))
        }

        projects.removeUserAssignments(projectId)

        return Output.success()
    }

    data class CommandInput(val projectId: Int) : Input

    sealed interface CommandFailure : Failure
    data class ProjectNotFound(val projectId: Int) : CommandFailure
    data class ProjectDoesNotHaveAssignedUser(val projectId: Int) : CommandFailure
}