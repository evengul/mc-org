package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.entities.project.CreateProjectRequest
import io.ktor.http.*

sealed interface GetCreateProjectInputFailure : CreateProjectFailure {
    data object NameMissing : GetCreateProjectInputFailure
}

object GetCreateProjectInputStep : Step<Parameters, GetCreateProjectInputFailure, CreateProjectRequest> {
    override suspend fun process(input: Parameters): Result<GetCreateProjectInputFailure, CreateProjectRequest> {
        val name = input["projectName"] ?: return Result.failure(GetCreateProjectInputFailure.NameMissing)
        val dimension = input["dimension"].mapDimension()
        val priority = input["priority"].mapPriority()
        val requiresPerimeter = input["requiresPerimeter"]?.toBooleanStrictOrNull() ?: false

        return Result.success(
            CreateProjectRequest(
                name = name,
                dimension = dimension,
                priority = priority,
                requiresPerimeter = requiresPerimeter
            )
        )
    }
}

private fun String?.mapDimension(): Dimension = when (this) {
    "OVERWORLD" -> Dimension.OVERWORLD
    "NETHER" -> Dimension.NETHER
    "THE_END" -> Dimension.THE_END
    else -> Dimension.OVERWORLD
}

private fun String?.mapPriority(): Priority = when (this) {
    "LOW" -> Priority.LOW
    "MEDIUM" -> Priority.MEDIUM
    "HIGH" -> Priority.HIGH
    else -> Priority.NONE
}