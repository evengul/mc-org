package app.mcorg.domain.api

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.model.projects.Project
import app.mcorg.domain.model.projects.SlimProject
import app.mcorg.domain.model.task.TaskStage
import app.mcorg.domain.model.users.User

interface Projects {
    fun getProject(id: Int, includeTasks: Boolean = false, includeDependencies: Boolean = false): Project?
    fun deleteProject(id: Int)
    fun getWorldProjects(id: Int): List<SlimProject>
    fun createProject(worldId: Int, name: String, dimension: Dimension, priority: Priority, requiresPerimeter: Boolean): Int

    fun changeProjectName(id: Int, name: String)

    fun archiveProject(id: Int)
    fun openProject(id: Int)

    fun getProjectAssignee(id: Int): User?
    fun assignProject(id: Int, userId: Int)
    fun removeProjectAssignment(id: Int)

    fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int
    fun addDoableTask(projectId: Int, name: String, priority: Priority): Int
    fun removeTask(id: Int)
    fun completeTask(id: Int)
    fun undoCompleteTask(id: Int)
    fun updateTaskStage(id: Int, stage: TaskStage)
    fun updateCountableTask(id: Int, needed: Int, done: Int)
    fun taskRequiresMore(id: Int, needed: Int)
    fun taskDoneMore(id: Int, doneMore: Int)
    fun editTaskRequirements(id: Int, needed: Int, done: Int)
    fun getTaskAssignee(id: Int): User?
    fun assignTask(id: Int, userId: Int)
    fun removeTaskAssignment(id: Int)
    fun removeUserAssignments(id: Int)

    fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int
    fun removeProjectDependencyToTask(dependencyId: Int)
}