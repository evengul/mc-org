package app.mcorg.domain.cqrs.commands.profile

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.api.Projects
import app.mcorg.domain.api.Users
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class DeleteProfileCommand (
    private val users: Users,
    private val projects: Projects,
    private val permissions: Permissions
) : Command<DeleteProfileCommand.CommandInput, DeleteProfileCommand.CommandSuccess, DeleteProfileCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (userId) = input

        if (!users.userExists(userId)) {
            return Output.failure(UserDoesNotExistFailure(userId))
        }

        projects.removeUserAssignments(userId)
        permissions.removeUserPermissions(userId)
        users.deleteUser(userId)

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val userId: Int
    ) : Input

    class CommandSuccess : Success
    sealed interface CommandFailure : Failure
    data class UserDoesNotExistFailure(val id: Int) : CommandFailure
}