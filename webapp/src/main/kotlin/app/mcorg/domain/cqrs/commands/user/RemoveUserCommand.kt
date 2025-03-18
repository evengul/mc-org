package app.mcorg.domain.cqrs.commands.user

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.api.Projects
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command
import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.api.Users

class RemoveUserCommand(
    private val users: Users,
    private val permissions: Permissions,
    private val projects: Projects
) : Command<RemoveUserCommand.CommandInput, RemoveUserCommand.CommandSuccess, RemoveUserCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (worldId, userId) = input

        if (!users.userExists(userId)) {
            return Output.failure(UserDoesNotExistFailure(userId))
        }

        if (!permissions.hasWorldPermission(userId, Authority.PARTICIPANT, worldId)) {
            return Output.failure(UserDoesNotBelongToWorldFailure(userId, worldId))
        }

        projects.removeUserAssignments(userId)
        permissions.removeWorldPermission(userId, worldId)

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val worldId: Int,
        val userId: Int
    ) : Input

    class CommandSuccess : Success

    interface CommandFailure: Failure
    data class UserDoesNotExistFailure(val userId: Int): CommandFailure
    data class UserDoesNotBelongToWorldFailure(val userId: Int, val worldId: Int): CommandFailure
}