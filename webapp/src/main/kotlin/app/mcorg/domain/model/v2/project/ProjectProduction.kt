package app.mcorg.domain.model.v2.project

data class ProjectProduction(
    val id: Int,
    val projectId: Int,
    val name: String,
    val ratePerHour: Int
)
