package app.mcorg.domain.cqrs.commands.project

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.api.Projects
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class AddProjectAssignmentCommand(
    private val permissions: Permissions,
    private val projects: Projects
) : Command<AddProjectAssignmentCommand.CommandInput, Success, AddProjectAssignmentCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, Success> {
        val (worldId, projectId, userId) = input

        if (permissions.getUsersInWorld(worldId).none { it.id == userId }) {
            return Output.failure(UserDoesNotExistInWorldFailure(worldId, userId))
        }

        projects.assignProject(projectId, userId)

        return Output.success()
    }

    data class CommandInput(val worldId: Int, val projectId: Int, val userId: Int) : Input
    sealed interface CommandFailure : Failure
    data class UserDoesNotExistInWorldFailure(val worldId: Int, val userId: Int) : CommandFailure
}