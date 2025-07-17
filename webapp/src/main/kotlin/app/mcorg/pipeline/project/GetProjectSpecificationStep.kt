package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetProjectSpecificationStepFailure
import io.ktor.http.*

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