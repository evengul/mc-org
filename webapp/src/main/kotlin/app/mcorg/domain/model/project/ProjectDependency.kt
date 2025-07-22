package app.mcorg.domain.model.project

data class ProjectDependency(
    val dependsOnProjectId: Int,
    val dependsOnTaskIds: Set<Int> = emptySet()
)
