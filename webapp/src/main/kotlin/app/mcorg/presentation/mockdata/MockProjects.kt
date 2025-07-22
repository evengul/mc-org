package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType

object MockProjects {
    val baseConstruction = Project(
        id = 1,
        worldId = MockWorlds.survivalWorld.id,
        name = "Base Construction",
        description = "Building the main base",
        type = ProjectType.BUILDING,
        stage = ProjectStage.RESOURCE_GATHERING,
        location = MinecraftLocation(
            x = 100,
            y = 70,
            z = 0,
            dimension = Dimension.OVERWORLD
        ),
        tasksTotal = 2,
        tasksCompleted = 0,
        createdAt = mockZonedDateTime(2023, 2, 1),
        updatedAt = mockZonedDateTime(2023, 5, 1)
    )

    val netherExploration = Project(
        id = 2,
        worldId = MockWorlds.survivalWorld.id,
        name = "Nether Exploration",
        description = "Exploring the Nether for resources",
        type = ProjectType.EXPLORATION,
        stage = ProjectStage.PLANNING,
        location = null,
        tasksTotal = 0,
        tasksCompleted = 0,
        createdAt = mockZonedDateTime(2023, 4, 1),
        updatedAt = mockZonedDateTime(2023, 5, 1)
    )

    fun getProjectsByWorldId(worldId: Int): List<Project> {
        return listOf(baseConstruction, netherExploration).filter { it.worldId == worldId }
    }
}