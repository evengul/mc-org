package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.failure.CreateWorldStepFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection

data class CreateWorldStep(val username: String) : Step<String, CreateWorldStepFailure, Int> {
    override suspend fun process(input: String): Result<CreateWorldStepFailure, Int> {
        return useConnection({ CreateWorldStepFailure.Other(it) }) {
            prepareStatement("insert into world (name, created_by, updated_by) values (?, ?, ?) returning id")
                .apply {
                    setString(1, input)
                    setString(2, username)
                    setString(3, username)
                }
                .getReturnedId(CreateWorldStepFailure.Other(DatabaseFailure.NoIdReturned))
        }
    }
}