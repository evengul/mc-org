package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage

data class UpdateProjectStageInput(
    val projectId: Int,
    val newStage: ProjectStage,
    val userId: Int
)

data class UpdateProjectStageOutput(
    val projectId: Int,
    val previousStage: ProjectStage,
    val newStage: ProjectStage,
    val updatedAt: java.time.ZonedDateTime
)
