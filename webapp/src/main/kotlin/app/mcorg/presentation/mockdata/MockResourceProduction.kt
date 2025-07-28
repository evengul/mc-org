package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.project.ProjectProduction

object MockResourceProduction {
    val wheatProduction = ProjectProduction(
        id = 1,
        projectId = MockProjects.wheatFarm.id,
        name = "Wheat",
        ratePerHour = 1200
    )

    val seedProduction = ProjectProduction(
        id = 2,
        projectId = MockProjects.wheatFarm.id,
        name = "Seeds",
        ratePerHour = 1000
    )

    fun getByProjectId(projectId: Int): List<ProjectProduction> {
        return listOf(wheatProduction, seedProduction).filter { it.projectId == projectId }
    }
}