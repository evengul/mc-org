package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidateCreateProjectInputFailure
import app.mcorg.pipeline.useConnection
import app.mcorg.presentation.entities.project.CreateProjectRequest

data class ValidateCreateProjectInputStep(val worldId: Int) : Step<CreateProjectRequest, ValidateCreateProjectInputFailure, CreateProjectRequest> {
    override suspend fun process(input: CreateProjectRequest): Result<ValidateCreateProjectInputFailure, CreateProjectRequest> {
        return useConnection({ ValidateCreateProjectInputFailure.Other(it) }) {
            prepareStatement("select id from project where name = ? and world_id = ?")
                .apply {
                    setString(1, input.name)
                    setInt(2, worldId)
                }
                .executeQuery()
                .let { resultSet ->
                    if (resultSet.next()) {
                        return@useConnection Result.failure(ValidateCreateProjectInputFailure.NameAlreadyExistsInWorld)
                    }
                }
            return@useConnection Result.success(input)
        }
    }
}
