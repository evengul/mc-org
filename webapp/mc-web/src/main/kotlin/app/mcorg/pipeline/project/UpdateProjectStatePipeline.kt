package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.event.ProjectStatusChanged
import app.mcorg.event.eventBus
import java.time.Instant
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.projectStateBadgeFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

suspend fun ApplicationCall.handleUpdateProjectState() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val bus = this.eventBus

    handlePipeline(
        onSuccess = { newState ->
            respondHtml(projectStateBadgeFragment(projectId, newState))
        }
    ) {
        val target = ValidateProjectStateInputStep.run(parameters)
        ValidateWorldMemberRole<ProjectState>(user, Role.ADMIN, worldId).run(target)
        val current = GetProjectStateStep.run(projectId)
        ValidateStateTransitionStep(current).run(target)
        val newState = UpdateProjectStateStep(projectId).run(target)
        bus.publish(ProjectStatusChanged(worldId, user.id, Instant.now(), projectId, current, newState))
        newState
    }
}

object ValidateProjectStateInputStep : Step<Parameters, AppFailure.ValidationError, ProjectState> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ProjectState> {
        val state = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "state", "Invalid project state",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { !it.isNullOrBlank() && runCatching { ProjectState.valueOf(it.uppercase()) }.isSuccess }
        ).process(input["state"])

        return state.map { ProjectState.valueOf(it!!.uppercase()) }
    }
}

object GetProjectStateStep : Step<Int, AppFailure, ProjectState> {
    override suspend fun process(input: Int): Result<AppFailure, ProjectState> {
        return DatabaseSteps.query<Int, ProjectState?>(
            sql = SafeSQL.select("SELECT state FROM projects WHERE id = ?"),
            parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
            resultMapper = { resultSet ->
                if (resultSet.next()) ProjectState.valueOf(resultSet.getString("state")) else null
            }
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound)
            else Result.success(it)
        }
    }
}

data class ValidateStateTransitionStep(val current: ProjectState) : Step<ProjectState, AppFailure.ValidationError, ProjectState> {
    override suspend fun process(input: ProjectState): Result<AppFailure.ValidationError, ProjectState> {
        return if (current.canTransitionTo(input)) {
            Result.success(input)
        } else {
            Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.CustomValidation("state", "Cannot transition from $current to $input"))
                )
            )
        }
    }
}

data class UpdateProjectStateStep(val projectId: Int) : Step<ProjectState, AppFailure.DatabaseError, ProjectState> {
    override suspend fun process(input: ProjectState): Result<AppFailure.DatabaseError, ProjectState> {
        return DatabaseSteps.update<ProjectState>(
            sql = SafeSQL.update("""
                UPDATE projects
                SET state = ?,
                    completed_at = CASE WHEN ? = 'DONE' THEN NOW() ELSE completed_at END,
                    updated_at = NOW()
                WHERE id = ?
            """),
            parameterSetter = { statement, state ->
                statement.setString(1, state.name)
                statement.setString(2, state.name)
                statement.setInt(3, projectId)
            }
        ).process(input).map { input }
    }
}
