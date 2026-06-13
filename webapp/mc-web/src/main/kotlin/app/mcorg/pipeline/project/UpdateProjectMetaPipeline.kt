package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.projectLocationEditFragment
import app.mcorg.presentation.templated.dsl.projectLocationViewFragment
import app.mcorg.presentation.templated.dsl.projectNameEditFragment
import app.mcorg.presentation.templated.dsl.projectNameViewFragment
import app.mcorg.presentation.templated.dsl.projectStateEditFragment
import app.mcorg.presentation.templated.dsl.projectStateViewFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

// ---------------------------------------------------------------------------
// GET field fragments (view / edit toggle)
// ---------------------------------------------------------------------------

private suspend fun ApplicationCall.renderMetaField(
    render: (project: Project, isAdmin: Boolean, edit: Boolean) -> String
) {
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> {
            val edit = request.queryParameters["mode"] == "edit" && isAdmin
            respondHtml(render(result.value, isAdmin, edit))
        }
        is Result.Failure -> defaultHandleError(result.error)
    }
}

suspend fun ApplicationCall.handleGetProjectNameField() = renderMetaField { project, isAdmin, edit ->
    if (edit) projectNameEditFragment(project) else projectNameViewFragment(project, isAdmin)
}

suspend fun ApplicationCall.handleGetProjectStateField() = renderMetaField { project, isAdmin, edit ->
    if (edit) projectStateEditFragment(project) else projectStateViewFragment(project, isAdmin)
}

suspend fun ApplicationCall.handleGetProjectLocationField() = renderMetaField { project, isAdmin, edit ->
    if (edit) projectLocationEditFragment(project) else projectLocationViewFragment(project, isAdmin)
}

// ---------------------------------------------------------------------------
// PATCH name
// ---------------------------------------------------------------------------

suspend fun ApplicationCall.handleUpdateProjectName() {
    val parameters = receiveParameters()
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline<Project>(
        onSuccess = { respondHtml(projectNameViewFragment(it, isAdmin = true)) }
    ) {
        val name = ValidateProjectNameInputStep.run(parameters)
        ValidateWorldMemberRole<String>(user, Role.ADMIN, worldId).run(name)
        UpdateProjectNameStep(projectId).run(name)
        GetProjectByIdStep.run(projectId)
    }
}

object ValidateProjectNameInputStep : Step<Parameters, AppFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String> =
        ValidationSteps.required("name") { AppFailure.ValidationError(listOf(it)) }
            .process(input)
            .flatMap { name ->
                ValidationSteps.validateLength("name", 3, 100) { AppFailure.ValidationError(listOf(it)) }.process(name)
            }
}

data class UpdateProjectNameStep(val projectId: Int) : Step<String, AppFailure.DatabaseError, String> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, String> =
        DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE projects SET name = ?, updated_at = NOW() WHERE id = ?"),
            parameterSetter = { statement, name ->
                statement.setString(1, name)
                statement.setInt(2, projectId)
            }
        ).process(input).map { input }
}

// ---------------------------------------------------------------------------
// PATCH state (inline editor — reuses the state steps, returns the meta field)
// ---------------------------------------------------------------------------

suspend fun ApplicationCall.handleUpdateProjectStateInline() {
    val parameters = receiveParameters()
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline<Project>(
        onSuccess = { respondHtml(projectStateViewFragment(it, isAdmin = true)) }
    ) {
        val target = ValidateProjectStateInputStep.run(parameters)
        ValidateWorldMemberRole<ProjectState>(user, Role.ADMIN, worldId).run(target)
        val current = GetProjectStateStep.run(projectId)
        ValidateStateTransitionStep(current).run(target)
        UpdateProjectStateStep(projectId).run(target)
        GetProjectByIdStep.run(projectId)
    }
}

// ---------------------------------------------------------------------------
// PATCH location (X/Z; Y and dimension default to 0 / OVERWORLD when first set)
// ---------------------------------------------------------------------------

suspend fun ApplicationCall.handleUpdateProjectLocation() {
    val parameters = receiveParameters()
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline<Project>(
        onSuccess = { respondHtml(projectLocationViewFragment(it, isAdmin = true)) }
    ) {
        val coords = ValidateProjectLocationInputStep.run(parameters)
        ValidateWorldMemberRole<Pair<Int, Int>>(user, Role.ADMIN, worldId).run(coords)
        UpdateProjectLocationStep(projectId).run(coords)
        GetProjectByIdStep.run(projectId)
    }
}

object ValidateProjectLocationInputStep : Step<Parameters, AppFailure.ValidationError, Pair<Int, Int>> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Pair<Int, Int>> {
        val x = ValidationSteps.requiredInt("x") { AppFailure.ValidationError(listOf(it)) }.process(input)
        val z = ValidationSteps.requiredInt("z") { AppFailure.ValidationError(listOf(it)) }.process(input)

        val errors = mutableListOf<ValidationFailure>()
        if (x is Result.Failure) errors.addAll(x.error.errors)
        if (z is Result.Failure) errors.addAll(z.error.errors)
        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors.toList()))
        }
        return Result.success(x.getOrNull()!! to z.getOrNull()!!)
    }
}

data class UpdateProjectLocationStep(val projectId: Int) : Step<Pair<Int, Int>, AppFailure.DatabaseError, Pair<Int, Int>> {
    override suspend fun process(input: Pair<Int, Int>): Result<AppFailure.DatabaseError, Pair<Int, Int>> =
        DatabaseSteps.update<Pair<Int, Int>>(
            sql = SafeSQL.update(
                """
                UPDATE projects
                SET location_x = ?,
                    location_z = ?,
                    location_y = COALESCE(location_y, 0),
                    location_dimension = COALESCE(location_dimension, 'OVERWORLD'),
                    updated_at = NOW()
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { statement, (x, z) ->
                statement.setInt(1, x)
                statement.setInt(2, z)
                statement.setInt(3, projectId)
            }
        ).process(input).map { input }
}
