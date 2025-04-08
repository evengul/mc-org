package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection

sealed interface CreateWorldStepFailure : CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : CreateWorldStepFailure
}

object CreateWorldStep : Step<String, CreateWorldStepFailure, Int> {
    override suspend fun process(input: String): Result<CreateWorldStepFailure, Int> {
        return useConnection({ CreateWorldStepFailure.Other(it) }) {
            prepareStatement("insert into world (name) values (?) returning id")
                .apply { setString(1, input) }
                .getReturnedId(CreateWorldStepFailure.Other(DatabaseFailure.NoIdReturned))
        }
    }
}