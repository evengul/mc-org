package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection
import app.mcorg.presentation.entities.project.CreateProjectRequest

sealed interface CreateProjectStepFailure : CreateProjectFailure {
    data class Other(val failure: DatabaseFailure) : CreateProjectStepFailure
}

data class CreateProjectStep(val worldId: Int, val currentUsername: String) : Step<CreateProjectRequest, CreateProjectStepFailure, Int> {
    override suspend fun process(input: CreateProjectRequest): Result<CreateProjectStepFailure, Int> {
        return useConnection({ CreateProjectStepFailure.Other(it) }) {
            prepareStatement("insert into project(world_id, name, archived, priority, requires_perimeter, dimension, assignee, created_by, updated_by) values (?, ?, false, ?, ?, ?, null, ?, ?) returning id")
                .apply {
                    setInt(1, worldId)
                    setString(2, input.name)
                    setString(3, input.priority.name)
                    setBoolean(4, input.requiresPerimeter)
                    setString(5, input.dimension.name)
                    setString(6, currentUsername)
                    setString(7, currentUsername)
                }.getReturnedId(CreateProjectStepFailure.Other(DatabaseFailure.NoIdReturned))
        }
    }
}