package app.mcorg.domain.cqrs.commands.project

import app.mcorg.domain.api.Projects
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class DeleteProjectCommand(
    private val projects: Projects
) : Command<DeleteProjectCommand.CommandInput, Success, DeleteProjectCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, Success> {
        val projectId = input.projectId

        if (!projects.projectExists(projectId)) {
            return Output.failure(ProjectNotFound(projectId))
        }

        projects.deleteProject(projectId)

        return Output.success()
    }

    data class CommandInput(val projectId: Int) : Input
    sealed interface CommandFailure : Failure
    data class ProjectNotFound(val projectId: Int) : CommandFailure
}