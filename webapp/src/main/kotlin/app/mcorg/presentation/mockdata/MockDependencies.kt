package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.project.ProjectDependency

object MockDependencies {
    val wheatFarmToBaseConstruction = ProjectDependency(
        dependentId = MockProjects.wheatFarm.id,
        dependentName = MockProjects.wheatFarm.name,
        dependentStage = MockProjects.wheatFarm.stage,
        dependencyId = MockProjects.baseConstruction.id,
        dependencyName = MockProjects.baseConstruction.name,
        dependencyStage = MockProjects.baseConstruction.stage,
    )

    val netherExplorationToBaseConstruction = ProjectDependency(
        dependentId = MockProjects.netherExploration.id,
        dependentName = MockProjects.netherExploration.name,
        dependentStage = MockProjects.netherExploration.stage,
        dependencyId = MockProjects.baseConstruction.id,
        dependencyName = MockProjects.baseConstruction.name,
        dependencyStage = MockProjects.baseConstruction.stage,
    )

    fun getDependenciesByProjectId(projectId: Int): List<ProjectDependency> {
        return listOf(wheatFarmToBaseConstruction, netherExplorationToBaseConstruction)
            .filter { it.dependentId == projectId }
    }

    fun getDependentsByProjectId(projectId: Int): List<ProjectDependency> {
        return listOf(wheatFarmToBaseConstruction, netherExplorationToBaseConstruction)
            .filter { it.dependencyId == projectId }
    }
}
