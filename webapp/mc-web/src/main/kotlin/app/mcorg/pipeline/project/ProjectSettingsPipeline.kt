package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.resources.GetStorageTrackingStep
import app.mcorg.pipeline.resources.SetStorageTrackedInput
import app.mcorg.pipeline.resources.SetStorageTrackedStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.ProjectSettingsData
import app.mcorg.presentation.templated.dsl.pages.projectSettingsPage
import app.mcorg.presentation.templated.dsl.pages.storageTrackingSectionHtml
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/**
 * GET /worlds/{worldId}/projects/{projectId}/settings
 *
 * Open to every world participant, like the project's own inline fields: what is here changes how
 * the project counts, which any member may already do by hand. The danger zone is drawn for admins
 * only, matching the admin gate on the delete route itself.
 */
suspend fun ApplicationCall.handleGetProjectSettings() {
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline(
        onSuccess = { data: ProjectSettingsData -> respondHtml(projectSettingsPage(user, data)) }
    ) {
        val project = GetProjectByIdStep.run(projectId)
        val tracking = GetStorageTrackingStep.run(projectId)
        val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success
        ProjectSettingsData(
            project = project,
            worldName = getWorldName(worldId),
            isWorldAdmin = isAdmin,
            tracking = tracking,
        )
    }
}

/**
 * PATCH /worlds/{worldId}/projects/{projectId}/settings/storage-tracked
 *
 * Form param `tracked`: `true` to follow the chests, `false` to go back to typed counts. Answers
 * with the section, redrawn from what the database now holds.
 */
suspend fun ApplicationCall.handleSetStorageTracked() {
    val parameters = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline(
        onSuccess = { tracking -> respondHtml(storageTrackingSectionHtml(worldId, projectId, tracking)) }
    ) {
        val tracked = ValidateStorageTrackedStep.run(parameters)
        SetStorageTrackedStep.run(SetStorageTrackedInput(projectId, tracked))
    }
}

private object ValidateStorageTrackedStep : Step<Parameters, AppFailure.ValidationError, Boolean> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Boolean> =
        when (val value = input["tracked"]?.toBooleanStrictOrNull()) {
            null -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidValue("tracked", listOf("true", "false"))))
            )
            else -> Result.success(value)
        }
}
