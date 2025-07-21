package app.mcorg.domain.model.project

data class ProjectProduction(
    val id: Int,
    val projectId: Int,
    val name: String,
    val ratePerHour: Int
)
