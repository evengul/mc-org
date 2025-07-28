package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskRequirement

object MockTasks {
    val gatherWood = Task(
        id = 1,
        projectId = MockProjects.baseConstruction.id,
        name = "Gather Wood",
        description = "Collect wood for construction",
        stage = ProjectStage.RESOURCE_GATHERING,
        priority = Priority.HIGH,
        requirements = listOf(
            TaskRequirement.item(1, "Oak Logs", 64).copy(collected = 32),
            TaskRequirement.item(2, "Birch Logs", 32).copy(collected = 16)
        )
    )

    val buildFoundation = Task(
        id = 2,
        projectId = MockProjects.baseConstruction.id,
        name = "Build Foundation",
        description = "Lay out the foundation for the base",
        stage = ProjectStage.BUILDING,
        priority = Priority.MEDIUM,
        requirements = listOf(
            TaskRequirement.action(3, "Clear the area"),
            TaskRequirement.action(4, "Mark the perimeter"),
            TaskRequirement.action(5, "Place foundation blocks")
        )
    )

    val planFarmLayout = Task(
        id = 3,
        projectId = MockProjects.wheatFarm.id,
        name = "Plan Farm Layout",
        description = "Design the layout for the wheat farm",
        stage = ProjectStage.PLANNING,
        priority = Priority.MEDIUM,
        requirements = listOf(
            TaskRequirement.action(6, "Measure available space"),
            TaskRequirement.action(7, "Sketch farm design")
        )
    )

    fun getTasksByProjectId(projectId: Int): List<Task> {
        return listOf(gatherWood, buildFoundation, planFarmLayout).filter { it.projectId == projectId }
    }
}