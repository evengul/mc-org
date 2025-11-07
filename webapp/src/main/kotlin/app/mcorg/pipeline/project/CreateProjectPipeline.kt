package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.world.projectItem
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

data class CreateProjectInput(
    val name: String,
    val description: String,
    val type: ProjectType,
)

suspend fun ApplicationCall.handleCreateProject() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                projectItem(it)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-projects-state")
            })
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectInputStep)
            .step(ValidateWorldMemberRole(user, Role.ADMIN, worldId))
            .step(CreateProjectStep(worldId))
            .step(GetProjectByIdStep)
    }
}

object ValidateProjectInputStep : Step<Parameters, AppFailure.ValidationError, CreateProjectInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateProjectInput> {
        val name = ValidationSteps.required("name") { AppFailure.ValidationError(listOf(it)) }
            .process(input)
            .flatMap { name -> ValidationSteps.validateLength("name", 3, 100) { AppFailure.ValidationError(listOf(it)) }.process(name) }

        val description = input["description"]?.let {
            ValidationSteps.validateLength("description", 0, 500) { e -> AppFailure.ValidationError(listOf(e)) }
                .process(it)
        } ?: Result.success("")

        val type = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "type",
            "Invalid project type",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = {
                !it.isNullOrBlank() && runCatching {
                    ProjectType.valueOf(it.uppercase())
                }.isSuccess
            }).process(input["type"]).map { ProjectType.valueOf(it!!.uppercase()) }

        val errors = mutableListOf<ValidationFailure>()
        if (name is Result.Failure) {
            errors.addAll(name.error.errors)
        }
        if (description is Result.Failure) {
            errors.addAll(description.error.errors)
        }
        if (type is Result.Failure) {
            errors.addAll(type.error.errors)
        }
        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors.toList()))
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

data class CreateProjectStep(val worldId: Int) : Step<CreateProjectInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateProjectInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<CreateProjectInput>(
            sql = SafeSQL.insert("""
                INSERT INTO projects (world_id, name, description, type, stage, location_x, location_y, location_z, location_dimension) 
                VALUES (?, ?, ?, ?, 'IDEA', 0, 0, 0, 'OVERWORLD') 
                RETURNING id
            """.trimIndent()),
            parameterSetter = { statement, (name, description, type) ->
                statement.setInt(1, worldId)
                statement.setString(2, name)
                statement.setString(3, description)
                statement.setString(4, type.name)
            }
        ).process(input)
    }
}
