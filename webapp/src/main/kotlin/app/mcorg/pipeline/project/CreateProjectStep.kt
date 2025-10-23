package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.CreateProjectFailures

data class CreateProjectStep(val worldId: Int) : Step<CreateProjectInput, CreateProjectFailures, Int> {
    override suspend fun process(input: CreateProjectInput): Result<CreateProjectFailures, Int> {
        return DatabaseSteps.update<CreateProjectInput, CreateProjectFailures>(
            insertProjectQuery,
            parameterSetter = { statement, (name, description, type) ->
                statement.setInt(1, worldId)
                statement.setString(2, name)
                statement.setString(3, description)
                statement.setString(4, type.name)
            },
            errorMapper = { CreateProjectFailures.DatabaseError }
        ).process(input)
    }
}
