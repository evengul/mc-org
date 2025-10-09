package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.CreateWorldFailures
import io.ktor.http.Parameters

object ValidateWorldInputStep : Step<Parameters, CreateWorldFailures, CreateWorldInput> {
    override suspend fun process(input: Parameters): Result<CreateWorldFailures, CreateWorldInput> {
        return WorldInputValidator.validateWorldInput(input)
            .mapError { CreateWorldFailures.ValidationError(it) }
            .map { validationResult ->
                CreateWorldInput(
                    name = validationResult.name,
                    description = validationResult.description,
                    version = validationResult.version
                )
            }
    }
}