package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.Project
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
        stageProgress = 0.0,
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
        stageProgress = 0.0,
        createdAt = mockZonedDateTime(2023, 4, 1),
        updatedAt = mockZonedDateTime(2023, 5, 1)
    )

    val wheatFarm = Project(
        id = 3,
        worldId = MockWorlds.survivalWorld.id,
        name = "Wheat Farm",
        description = "Setting up an automatic wheat farm",
        type = ProjectType.FARMING,
        stage = ProjectStage.COMPLETED,
        location = MinecraftLocation(
            x = 150,
            y = 70,
            z = 120,
            dimension = Dimension.OVERWORLD
        ),
        tasksTotal = 1,
        tasksCompleted = 0,
        stageProgress = 100.0,
        createdAt = mockZonedDateTime(2023, 3, 1),
        updatedAt = mockZonedDateTime(2024, 3, 1)
    )

    fun getProjectsByWorldId(worldId: Int, includeCompleted: Boolean = false): List<Project> {
        return listOf(baseConstruction, netherExploration, wheatFarm).filter { it.worldId == worldId && (includeCompleted || it.stage != ProjectStage.COMPLETED) }
    }

    fun getById(id: Int): Project? {
        return listOf(baseConstruction, netherExploration, wheatFarm).find { it.id == id }
    }
}