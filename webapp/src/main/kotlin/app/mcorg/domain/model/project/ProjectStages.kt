package app.mcorg.domain.model.project

import java.time.ZonedDateTime

data class ProjectStageChange(
    val projectId: Int,
    val stage: ProjectStage,
    val relatedTasks: List<String>,
    val enteredOn: ZonedDateTime,
)
