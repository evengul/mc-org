package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.CreateProjectFailures
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters

object ValidateProjectInputStep : Step<Parameters, CreateProjectFailures, CreateProjectInput> {
    override suspend fun process(input: Parameters): Result<CreateProjectFailures, CreateProjectInput> {
        val name = ValidationSteps.required("name", { CreateProjectFailures.ValidationError(listOf(it)) })
            .process(input)
        val description = ValidationSteps.optional("description")
            .process(input)
        val type = ValidationSteps.validateCustom<CreateProjectFailures.ValidationError, String?>(
            "type",
            "Invalid project type",
            errorMapper = { CreateProjectFailures.ValidationError(listOf(it)) },
            predicate = {
                !it.isNullOrBlank() && runCatching {
                    ProjectType.valueOf(it.uppercase())
                }.isSuccess
            }).process(input["type"]).map { ProjectType.valueOf(it!!.uppercase()) }

        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) {
            errors.addAll(name.error.errors)
        }
        if (type is Result.Failure) {
            errors.addAll(type.error.errors)
        }
        if (errors.isNotEmpty()) {
            return Result.failure(CreateProjectFailures.ValidationError(errors.toList()))
        }
        return Result.success(
            CreateProjectInput(
                name = name.getOrNull()!!,
                description = description.getOrNull() ?: "",
                type = type.getOrNull()!!
            )
        )
    }
}
