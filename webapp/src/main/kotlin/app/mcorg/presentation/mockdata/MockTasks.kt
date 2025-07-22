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
            TaskRequirement.item("Oak Logs", 64).copy(collected = 32),
            TaskRequirement.item("Birch Logs", 32).copy(collected = 16)
        )
    )

    fun getTasksByProjectId(projectId: Int): List<Task> {
        return listOf(gatherWood).filter { it.projectId == projectId }
    }
}