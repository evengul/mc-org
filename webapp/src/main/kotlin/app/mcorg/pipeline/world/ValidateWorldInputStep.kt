package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.CreateWorldFailures
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

object ValidateWorldInputStep : Step<Parameters, CreateWorldFailures, CreateWorldInput> {
    override suspend fun process(input: Parameters): Result<CreateWorldFailures, CreateWorldInput> {
        val name = ValidationSteps.required("name", { CreateWorldFailures.ValidationError(listOf(it)) })
            .process(input)
        val description = ValidationSteps.optional("description")
            .process(input)
        val version = ValidationSteps.validateCustom<CreateWorldFailures.ValidationError, String?>(
            "version",
            "Invalid Minecraft version",
            errorMapper = { CreateWorldFailures.ValidationError(listOf(it)) },
            predicate = {
                !it.isNullOrBlank() && runCatching {
                    MinecraftVersion.fromString(it)
                }.isSuccess
            }).process(input["version"]).map { MinecraftVersion.fromString(it!!) }

        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) {
            errors.addAll(name.error.errors)
        }
        if (version is Result.Failure) {
            errors.addAll(version.error.errors)
        }
        if (errors.isNotEmpty()) {
            return Result.failure(CreateWorldFailures.ValidationError(errors.toList()))
        }
        return Result.success(
            CreateWorldInput(
                name = name.getOrNull()!!,
                description = description.getOrNull() ?: "",
                version = version.getOrNull()!!
            )
        )
    }
}