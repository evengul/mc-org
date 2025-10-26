package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateProjectStageFailures
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.project.CreateTaskModalTab
import app.mcorg.presentation.templated.project.createTaskModal
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateProjectStage() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    executePipeline(
        onSuccess = { result: UpdateProjectStageOutput ->
            // Get updated project data to return updated header
            when (val getProjectResult = GetProjectByIdStep.process(projectId)) {
                is Result.Success -> {
                    val updatedProject = getProjectResult.value
                    // Return HTML fragment for HTMX to replace the entire project header content
                    respondHtml(createHTML().div {
                        div("project-header-content") {
                            id = "project-header-content" // Target for HTMX replacement

                            div("project-header-start") {
                                chipComponent {
                                    variant = ChipVariant.ACTION
                                    +updatedProject.stage.toPrettyEnumName()
                                }
                                p("subtle") {
                                    +"•"
                                }
                                p("subtle") {
                                    +"${updatedProject.type.toPrettyEnumName()} Project"
                                }
                                updatedProject.location?.let { location ->
                                    p("subtle") {
                                        +"•"
                                    }
                                    chipComponent {
                                        variant = ChipVariant.NEUTRAL
                                        text = "${location.x}, ${location.y}, ${location.z}"
                                    }
                                }
                            }
                            div("project-header-end") {
                                select {
                                    id = "project-stage-selector"
                                    name = "stage"

                                    // HTMX attributes for dynamic stage updates
                                    attributes["hx-patch"] = "/app/worlds/${updatedProject.worldId}/projects/${updatedProject.id}/stage"
                                    attributes["hx-target"] = "#project-header-content"
                                    attributes["hx-swap"] = "outerHTML"
                                    attributes["hx-trigger"] = "change"

                                    ProjectStage.entries.forEach { stage ->
                                        option {
                                            value = stage.name
                                            if (stage == updatedProject.stage) {
                                                selected = true
                                            }
                                            +stage.toPrettyEnumName()
                                        }
                                    }
                                }
                                createTaskModal(updatedProject, itemNames, CreateTaskModalTab.ITEM_REQUIREMENT)
                            }
                        }
                    })
                }
                is Result.Failure -> {
                    // Fallback response if we can't get updated project data
                    respondHtml(createHTML().div {
                        div("notice notice--success") {
                            +"Project stage updated to ${result.newStage.toPrettyEnumName()}"
                        }
                    })
                }
            }
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
