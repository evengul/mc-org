package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.*

sealed interface GetProjectSpecificationStepFailure : GetProjectsFailure

object GetProjectSpecificationFromFormStep : Step<Parameters, GetProjectSpecificationStepFailure, ProjectSpecification> {
    override suspend fun process(input: Parameters): Result<GetProjectSpecificationStepFailure, ProjectSpecification> {
        return Result.success(
            ProjectSpecification(
                search = input["search"],
                hideCompleted = input["hideCompleted"] == "on",
            )
        )
    }
}