package app.mcorg.domain.model.project

data class ProjectDependency(
    val dependentId: Int,
    val dependentName: String,
    val dependentStage: ProjectStage,
    val dependencyId: Int,
    val dependencyName: String,
    val dependencyStage: ProjectStage
)
