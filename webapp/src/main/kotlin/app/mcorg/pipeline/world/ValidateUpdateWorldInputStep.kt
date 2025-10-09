package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

object ValidateUpdateWorldInputStep : Step<Parameters, UpdateWorldFailures, UpdateWorldInput> {
    override suspend fun process(input: Parameters): Result<UpdateWorldFailures, UpdateWorldInput> {
        return WorldInputValidator.validateWorldInput(input)
            .mapError { UpdateWorldFailures.ValidationError(it) }
            .map { validationResult ->
                UpdateWorldInput(
                    name = validationResult.name,
                    description = validationResult.description,
                    version = validationResult.version
                )
            }
    }
}
