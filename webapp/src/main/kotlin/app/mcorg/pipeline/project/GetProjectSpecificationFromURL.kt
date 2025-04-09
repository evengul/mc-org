package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.mappers.URLMappers
import app.mcorg.presentation.mappers.project.projectFilterURLMapper

sealed interface GetProjectSpecificationFromURLFailure : CreateProjectFailure

object GetProjectSpecificationFromURL : Step<String?, GetProjectSpecificationFromURLFailure, ProjectSpecification> {
    override suspend fun process(input: String?): Result<GetProjectSpecificationFromURLFailure, ProjectSpecification> {
        return Result.success(URLMappers.projectFilterURLMapper(input))
    }
}