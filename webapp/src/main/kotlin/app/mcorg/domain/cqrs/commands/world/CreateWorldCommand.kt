package app.mcorg.domain.cqrs.commands.world

import app.mcorg.domain.api.Permissions
import app.mcorg.domain.api.Users
import app.mcorg.domain.api.Worlds
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command
import app.mcorg.domain.model.permissions.Authority

class CreateWorldCommand(
    private val worlds: Worlds,
    private val permissions: Permissions,
    private val users: Users
) : Command<CreateWorldCommand.CommandInput, CreateWorldCommand.CommandSuccess, CreateWorldCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (creatingUserId, name) = input
        if (worlds.worldExistsByName(name)) {
            return Output.failure(WorldNameAlreadyExistsFailure(name))
        }

        val worldId = worlds.createWorld(name)
        permissions.addWorldPermission(creatingUserId, worldId, Authority.OWNER)
        users.selectWorld(creatingUserId, worldId)

        return Output.success(CommandSuccess(worldId))
    }

    data class CommandInput(
        val creatingUserId: Int,
        val name: String
    ) : Input

    data class CommandSuccess(val worldId: Int) : Success
    sealed interface CommandFailure : Failure
    data class WorldNameAlreadyExistsFailure(val name: String) : CommandFailure
}