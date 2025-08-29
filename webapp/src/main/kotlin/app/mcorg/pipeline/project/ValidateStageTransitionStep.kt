package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.UpdateProjectStageFailures
import io.ktor.http.Parameters

data class StageTransitionInput(
    val newStage: ProjectStage,
    val currentStage: ProjectStage
)

object ValidateStageTransitionStep : Step<Parameters, UpdateProjectStageFailures, ProjectStage> {
    override suspend fun process(input: Parameters): Result<UpdateProjectStageFailures, ProjectStage> {
        val stageParam = ValidationSteps.validateCustom<UpdateProjectStageFailures.ValidationError, String?>(
            "stage",
            "Invalid project stage",
            errorMapper = { UpdateProjectStageFailures.ValidationError(listOf(it)) },
            predicate = { stage ->
                !stage.isNullOrBlank() && runCatching {
                    ProjectStage.valueOf(stage.uppercase())
                }.isSuccess
            }
        ).process(input["stage"])

        return when (stageParam) {
            is Result.Failure -> stageParam
            is Result.Success -> {
                val stage = ProjectStage.valueOf(stageParam.value!!.uppercase())
                Result.success(stage)
            }
        }
    }
}

object ValidateStageProgressionStep : Step<StageTransitionInput, UpdateProjectStageFailures, ProjectStage> {
    override suspend fun process(input: StageTransitionInput): Result<UpdateProjectStageFailures, ProjectStage> {
        val currentStage = input.currentStage
        val newStage = input.newStage

        // Allow staying in the same stage
        if (currentStage == newStage) {
            return Result.success(newStage)
        }

        // Define logical progression rules
        val isValidTransition = when (currentStage) {
            ProjectStage.IDEA -> {
                // From IDEA: can go to DESIGN or skip forward to any stage
                true // Allow flexible progression from initial stage
            }
            ProjectStage.DESIGN -> {
                // From DESIGN: can go to PLANNING or forward, but not backwards to IDEA
                newStage != ProjectStage.IDEA
            }
            ProjectStage.PLANNING -> {
                // From PLANNING: can go to RESOURCE_GATHERING or forward, but not backwards
                newStage.order >= ProjectStage.RESOURCE_GATHERING.order
            }
            ProjectStage.RESOURCE_GATHERING -> {
                // From RESOURCE_GATHERING: can go to BUILDING or forward, but not too far backwards
                newStage.order >= ProjectStage.DESIGN.order // Allow going back to DESIGN if needed
            }
            ProjectStage.BUILDING -> {
                // From BUILDING: can go to TESTING or COMPLETED, or back to RESOURCE_GATHERING if needed
                newStage == ProjectStage.TESTING ||
                        newStage == ProjectStage.COMPLETED ||
                        newStage == ProjectStage.RESOURCE_GATHERING
            }
            ProjectStage.TESTING -> {
                // From TESTING: can go to COMPLETED or back to BUILDING for fixes
                newStage == ProjectStage.COMPLETED || newStage == ProjectStage.BUILDING
            }
            ProjectStage.COMPLETED -> {
                // From COMPLETED: can go back to any stage for modifications/improvements
                true // Allow full flexibility for completed projects
            }
        }

        return if (isValidTransition) {
            Result.success(newStage)
        } else {
            Result.failure(UpdateProjectStageFailures.InvalidStageTransition)
        }
    }
}
