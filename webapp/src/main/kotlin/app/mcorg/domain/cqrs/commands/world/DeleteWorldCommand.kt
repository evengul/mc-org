package app.mcorg.domain.cqrs.commands.world

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.api.Users
import app.mcorg.domain.api.Worlds
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class DeleteWorldCommand(
    private val worlds: Worlds,
    private val permissions: Permissions,
    private val users: Users
) : Command<DeleteWorldCommand.CommandInput, DeleteWorldCommand.CommandSuccess, DeleteWorldCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (id) = input

        users.unSelectWorldForAll(id)
        permissions.removeWorldPermissionForAll(id)
        worlds.deleteWorld(id)

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val id: Int
    ) : Input

    class CommandSuccess : Success
    sealed interface CommandFailure : Failure
}