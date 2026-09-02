package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.event.ProjectCreated
import app.mcorg.event.actorDisplayName
import app.mcorg.event.eventBus
import java.time.Instant
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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
    val bus = this.eventBus
    val isHtmx = request.headers["HX-Request"] == "true"

    handlePipeline(
        onSuccess = { _ ->
            if (isHtmx) {
                // The Field Log groups projects by state; a full refresh places the new
                // project in the right section without fragment bookkeeping. This used to be
                // one of two branches, paired with OOB swaps that kept the plan view's card
                // list in step; the plan view is gone (MCO-474) and the reload does both.
                response.headers.append("HX-Redirect", "/worlds/$worldId/projects")
                respondHtml("")
            } else {
                response.headers.append("Location", "/worlds/$worldId/projects")
                respond(HttpStatusCode.SeeOther, "")
            }
        }
    ) {
        val input = ValidateProjectInputStep.run(parameters)
        ValidateWorldMemberRole<CreateProjectInput>(user, Role.ADMIN, worldId).run(input)
        val projectId = CreateProjectStep(worldId).run(input)
        CacheManager.onProjectCreated(worldId, projectId)
        bus.publish(ProjectCreated(worldId, user.id, Instant.now(), projectId, input.name, input.type, actorName = user.actorDisplayName()))
        projectId
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
                INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension)
                VALUES (?, ?, ?, ?, 'IDEA', 'PENDING', NULL, NULL, NULL, NULL)
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
