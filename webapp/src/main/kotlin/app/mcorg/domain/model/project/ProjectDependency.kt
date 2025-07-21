package app.mcorg.domain.model.v2.project

data class ProjectDependency(
    val dependsOnProjectId: Int,
    val dependsOnTaskIds: Set<Int>
)
