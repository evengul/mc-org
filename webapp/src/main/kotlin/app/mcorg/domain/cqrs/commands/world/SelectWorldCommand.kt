package app.mcorg.domain.cqrs.commands.world

import app.mcorg.domain.api.Users
import app.mcorg.domain.api.Worlds
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class SelectWorldCommand(
    private val worlds: Worlds,
    private val users: Users
) : Command<SelectWorldCommand.CommandInput, SelectWorldCommand.CommandSuccess, SelectWorldCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (userId, worldId) = input

        if (!worlds.worldExists(worldId)) {
            return Output.failure(WorldDoesNotExistFailure(worldId))
        }

        users.selectWorld(userId, worldId)

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val userId: Int,
        val worldId: Int
    ) : Input

    class CommandSuccess : Success
    sealed interface CommandFailure : Failure
    data class WorldDoesNotExistFailure(val worldId: Int) : CommandFailure
}