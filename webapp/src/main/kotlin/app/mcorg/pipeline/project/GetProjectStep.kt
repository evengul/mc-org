package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.Project
import app.mcorg.domain.model.projects.ProjectDependency
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.project.ProjectEnumMappers.toDimension
import app.mcorg.pipeline.project.ProjectEnumMappers.toPriority
import app.mcorg.pipeline.project.ProjectEnumMappers.toTaskStage
import app.mcorg.pipeline.project.ProjectEnumMappers.toTaskType
import app.mcorg.pipeline.useConnection
import java.sql.Connection
import java.sql.ResultSet

sealed interface GetProjectStepFailure : GetProjectFailure, CreateProjectFailure, AssignProjectFailure {
    data object NotFound : GetProjectStepFailure
    data class Other(val failure: DatabaseFailure) : GetProjectStepFailure
}

data class GetProjectStep(val include: Set<Include> = Include.none()) : Step<Int, GetProjectStepFailure, Project> {

    override suspend fun process(input: Int): Result<GetProjectStepFailure, Project> {
        return useConnection({ GetProjectStepFailure.Other(it) }) {
            prepareStatement("SELECT world_id, p.id as project_id, name, archived, priority, dimension, assignee, u.username as username, requires_perimeter FROM project p left join users u on p.assignee = u.id WHERE p.id = ?")
                .apply { setInt(1, input) }
                .executeQuery()
                .let {
                    if (!it.next()) return@let Result.failure(GetProjectStepFailure.NotFound)
                    val project = it.getProject()
                    if (include.contains(Include.Tasks)) {
                        addTasks(project)
                    }
                    if (include.contains(Include.Dependencies)) {
                        addDependencies(project)
                    }
                    return@let Result.success(project.copy(progress = getProjectProgress(this, project.id)))
                }
        }
    }

    private fun Connection.addTasks(project: Project) {
        prepareStatement("select task.id,name,priority,needed,done,type,stage,u.id as user_id,u.username as username from task left join users u on task.assignee = u.id where project_id = ? order by task.name")
            .apply { setInt(1, project.id) }
            .executeQuery()
            .apply {
                while (next()) {
                    val userId = getInt("user_id")
                    val username = getString("username")
                    val user = if (userId > 0 && username != null) User(userId, username) else null
                    project.tasks.add(
                        Task(
                            id = getInt("id"),
                            name = getString("name"),
                            needed = getInt("needed"),
                            done = getInt("done"),
                            priority = getString("priority").toPriority(),
                            dependencies = mutableListOf(),
                            assignee = user,
                            taskType = getString("type").toTaskType(),
                            stage = getString("stage").toTaskStage()
                        )
                    )
                }
            }
    }

    private fun Connection.addDependencies(project: Project){
        prepareStatement("select d.id, dependant_task_id, project_dependency_id, d.priority as priority from project_dependency d join task t on t.id = d.dependant_task_id where t.project_id = ?")
            .apply { setInt(1, project.id) }
            .executeQuery()
            .apply {
                while (next()) {
                    val taskId = getInt("dependant_task_id")
                    val priority = getString("priority").toPriority()

                    project.tasks
                        .find { task -> task.id == taskId }
                        ?.dependencies
                        ?.add(ProjectDependency(project.id, priority))
                }
            }
    }

    private fun ResultSet.getProject(): Project {
        val userId = getInt("assignee").takeIf { id -> id > 0 }
        val username = getString("username")
        val user = if(userId != null && username != null) User(userId, username) else null
        return Project(
            worldId = getInt("world_id"),
            id = getInt("project_id"),
            name = getString("name"),
            archived = getBoolean("archived"),
            priority = getString("priority").toPriority(),
            dimension = getString("dimension").toDimension(),
            assignee = user,
            progress = 0.0,
            requiresPerimeter = getBoolean("requires_perimeter"),
            tasks = mutableListOf()
        )
    }

    sealed interface Include {
        data object Tasks : Include
        data object Dependencies : Include

        companion object {
            fun all() = setOf(Tasks, Dependencies)
            fun none() = emptySet<Include>()
            fun onlyTasks() = setOf(Tasks)
        }
    }
}