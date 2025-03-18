package app.mcorg.domain.cqrs.commands.profile

import app.mcorg.domain.api.Users
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command

class ToggleTechnicalPlayerCommand(
    private val users: Users,
) : Command<ToggleTechnicalPlayerCommand.CommandInput, ToggleTechnicalPlayerCommand.CommandSuccess, ToggleTechnicalPlayerCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (userId, isTechnical) = input

        if (isTechnical) {
            users.isTechnical(userId)
        } else {
            users.isNotTechnical(userId)
        }

        return Output.success(CommandSuccess())
    }

    data class CommandInput(
        val userId: Int,
        val isTechnical: Boolean
    ) : Input

    class CommandSuccess : Success
    class CommandFailure : Failure
}