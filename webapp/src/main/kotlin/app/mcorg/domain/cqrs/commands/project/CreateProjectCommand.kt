package app.mcorg.domain.cqrs.commands.project

import app.mcorg.domain.api.Projects
import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success
import app.mcorg.domain.cqrs.commands.Command
import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority

class CreateProjectCommand(
    private val projects: Projects,
) : Command<CreateProjectCommand.CommandInput, CreateProjectCommand.CommandSuccess, CreateProjectCommand.CommandFailure> {

    override fun execute(input: CommandInput): Output<CommandFailure, CommandSuccess> {
        val (
            worldId,
            name,
            priority,
            dimension,
            requiresPerimeter
        ) = input
        if (projects.projectExistsByWorldAndName(worldId, name)) {
            return Output.failure(ProjectNameAlreadyExistsFailure(name))
        }

        val projectId = projects.createProject(worldId, name, dimension, priority, requiresPerimeter)

        return Output.success(CommandSuccess(projectId))
    }

    data class CommandInput(
        val worldId: Int,
        val name: String,
        val priority: Priority,
        val dimension: Dimension,
        val requiresPerimeter: Boolean
    ) : Input

    data class CommandSuccess(val projectId: Int) : Success
    sealed interface CommandFailure : Failure
    data class ProjectNameAlreadyExistsFailure(val name: String) : CommandFailure
}
