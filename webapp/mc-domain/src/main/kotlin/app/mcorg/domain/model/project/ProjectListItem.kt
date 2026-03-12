package app.mcorg.domain.model.project

data class ProjectListItem(
    val id: Int,
    val name: String,
    val stage: ProjectStage,
    val tasksTotal: Int,
    val tasksDone: Int,
    val resourcesRequired: Int,
    val resourcesGathered: Int,
    val nextTaskName: String?
)
