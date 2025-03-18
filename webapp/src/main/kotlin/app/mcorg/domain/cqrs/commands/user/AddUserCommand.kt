package app.mcorg.domain.cqrs.commands.user

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command
import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.api.Users

class AddUserCommand(
    private val users: Users,
    private val permissions: Permissions
) :
    Command<AddUserCommand.CommandInput, AddUserCommand.CommandSuccess, AddUserCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (worldId, username) = input
        val user = users.getUser(username) ?:
            return Output.failure(UserDoesNotExistFailure(input.username))

        val worldPermissions = permissions.getUsersInWorld(worldId)

        if (worldPermissions.any { it.username == username }) {
            return Output.failure(UserAlreadyExistFailure(input.username))
        }

        permissions.addWorldPermission(user.id, worldId, Authority.PARTICIPANT)

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val worldId: Int,
        val username: String
    ) : Input

    class CommandSuccess : Success
    sealed interface CommandFailure : Failure
    data class UserDoesNotExistFailure(val username: String) : CommandFailure
    data class UserAlreadyExistFailure(val username: String) : CommandFailure
}