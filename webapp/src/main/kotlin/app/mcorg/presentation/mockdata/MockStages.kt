package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectStageChange

object MockStages {
    val baseConstruction = listOf(
        ProjectStageChange(
            projectId = MockProjects.baseConstruction.id,
            stage = ProjectStage.IDEA,
            enteredOn = mockZonedDateTime(2023, 2, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.baseConstruction.id,
            stage = ProjectStage.DESIGN,
            enteredOn = mockZonedDateTime(2023, 3, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.baseConstruction.id,
            stage = ProjectStage.PLANNING,
            enteredOn = mockZonedDateTime(2023, 4, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.baseConstruction.id,
            stage = ProjectStage.RESOURCE_GATHERING,
            enteredOn = mockZonedDateTime(2023, 5, 1),
            relatedTasks = MockTasks.getTasksByProjectId(MockProjects.baseConstruction.id).map { it.name }
        )
    )

    val netherExploration = listOf(
        ProjectStageChange(
            projectId = MockProjects.netherExploration.id,
            stage = ProjectStage.PLANNING,
            enteredOn = mockZonedDateTime(2023, 4, 1),
            relatedTasks = emptyList()
        )
    )

    val wheatFarm = listOf(
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.IDEA,
            enteredOn = mockZonedDateTime(2023, 3, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.DESIGN,
            enteredOn = mockZonedDateTime(2023, 4, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.PLANNING,
            enteredOn = mockZonedDateTime(2023, 5, 1),
            relatedTasks = MockTasks.getTasksByProjectId(MockProjects.wheatFarm.id).filter { it.stage == ProjectStage.PLANNING }.map { it.name }
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.RESOURCE_GATHERING,
            enteredOn = mockZonedDateTime(2023, 6, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.BUILDING,
            enteredOn = mockZonedDateTime(2023, 7, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.TESTING,
            enteredOn = mockZonedDateTime(2023, 8, 1),
            relatedTasks = emptyList()
        ),
        ProjectStageChange(
            projectId = MockProjects.wheatFarm.id,
            stage = ProjectStage.COMPLETED,
            enteredOn = mockZonedDateTime(2024, 3, 1),
            relatedTasks = emptyList()
        )
    )

    fun getByProjectId(projectId: Int): List<ProjectStageChange> {
        return when (projectId) {
            MockProjects.baseConstruction.id -> baseConstruction
            MockProjects.netherExploration.id -> netherExploration
            MockProjects.wheatFarm.id -> wheatFarm
            else -> emptyList()
        }.sortedBy { it.stage.order }
    }
}