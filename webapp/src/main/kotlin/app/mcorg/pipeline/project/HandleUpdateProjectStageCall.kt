package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateProjectStageFailures
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectStageSelector
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateProjectStage() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onSuccess = { result: UpdateProjectStageOutput ->
            respondHtml(createHTML().select {
                projectStageSelector(worldId, projectId, result.newStage)
            } + createHTML().span {
                hxOutOfBands("innerHTML:#project-stage-chip")
                + result.newStage.toPrettyEnumName()
            })
        },
        onFailure = { failure: UpdateProjectStageFailures ->
            val errorMessage = when (failure) {
                is UpdateProjectStageFailures.ValidationError ->
                    "Invalid stage selection: ${failure.errors.joinToString(", ") { it.toString() }}"
                is UpdateProjectStageFailures.DatabaseError ->
                    "Unable to update project stage due to a database error. Please try again."
                is UpdateProjectStageFailures.ProjectNotFound ->
                    "Project not found. Please refresh the page and try again."
                is UpdateProjectStageFailures.InsufficientPermissions ->
                    "You don't have permission to update this project's stage. Member+ role required."
                is UpdateProjectStageFailures.InvalidStageTransition ->
                    "This stage transition is not allowed. Please select a stage that follows logical project progression."
            }
            respondBadRequest(errorMessage)
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateStageTransitionStep)
            .step(ValidatePermissionsStepAdapter(user, projectId))
            .step(ValidateStageProgressionStepAdapter(projectId))
            .step(UpdateStageStepAdapter(projectId, user.id))
    }
}

// Adapter steps to integrate with executePipeline pattern
class ValidatePermissionsStepAdapter(
    private val user: TokenProfile,
    private val projectId: Int
) : Step<ProjectStage, UpdateProjectStageFailures, ProjectStage> {
    override suspend fun process(input: ProjectStage): Result<UpdateProjectStageFailures, ProjectStage> {
        val permissionInput = ValidateProjectStagePermissionsInput(user, projectId)
        return ValidateProjectStagePermissionsStep.process(permissionInput).map { input }
    }
}

class ValidateStageProgressionStepAdapter(
    private val projectId: Int
) : Step<ProjectStage, UpdateProjectStageFailures, ProjectStage> {
    override suspend fun process(input: ProjectStage): Result<UpdateProjectStageFailures, ProjectStage> {
        // Get current project stage for progression validation
        return when (val getProjectResult = GetProjectByIdStep.process(projectId)) {
            is Result.Success -> {
                val currentProject = getProjectResult.value
                val transitionInput = StageTransitionInput(input, currentProject.stage)
                ValidateStageProgressionStep.process(transitionInput)
            }
            is Result.Failure -> {
                Result.failure(UpdateProjectStageFailures.ProjectNotFound)
            }
        }
    }
}

class UpdateStageStepAdapter(
    private val projectId: Int,
    private val userId: Int
) : Step<ProjectStage, UpdateProjectStageFailures, UpdateProjectStageOutput> {
    override suspend fun process(input: ProjectStage): Result<UpdateProjectStageFailures, UpdateProjectStageOutput> {
        val updateInput = UpdateProjectStageInput(projectId, input, userId)
        return UpdateProjectStageStep.process(updateInput)
    }
}
