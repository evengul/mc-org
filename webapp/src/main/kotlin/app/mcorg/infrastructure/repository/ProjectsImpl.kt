package app.mcorg.infrastructure.repository

import app.mcorg.domain.api.Projects
import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.*
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskStage
import app.mcorg.domain.model.task.TaskStages
import app.mcorg.domain.model.task.TaskType
import app.mcorg.domain.model.users.User
import app.mcorg.domain.model.projects.Priority
import java.sql.Connection

class ProjectsImpl : Projects, Repository() {
    override fun getProject(id: Int, includeTasks: Boolean, includeDependencies: Boolean): Project? {
        getConnection().use { conn ->
            val project = conn
                .prepareStatement("SELECT world_id, p.id as project_id, name, archived, priority, dimension, assignee, u.username as username, requires_perimeter FROM project p left join users u on p.assignee = u.id WHERE p.id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .let {
                    if (!it.next()) return@let null
                    else {
                        val progress = getProjectProgress(conn, projectId = id)
                        val userId = it.getInt("assignee").takeIf { id -> id > 0 }
                        val username = it.getString("username")
                        val user = if(userId != null && username != null) User(userId, username) else null
                        return@let Project(
                            worldId = it.getInt("world_id"),
                            id = it.getInt("project_id"),
                            name = it.getString("name"),
                            archived = it.getBoolean("archived"),
                            priority = it.getString("priority").toPriority(),
                            dimension = it.getString("dimension").toDimension(),
                            assignee = user,
                            progress = progress,
                            requiresPerimeter = it.getBoolean("requires_perimeter"),
                            tasks = mutableListOf()
                        )
                    }
                }

            if (project == null) return null

            if (includeTasks) {
                conn.prepareStatement("select task.id,name,priority,needed,done,type,stage,u.id as user_id,u.username as username from task left join users u on task.assignee = u.id where project_id = ? order by task.name")
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

                if (includeDependencies) {
                    conn.prepareStatement("select d.id, dependant_task_id, project_dependency_id, d.priority as priority from project_dependency d join task t on t.id = d.dependant_task_id where t.project_id = ?")
                        .apply { setInt(1, id) }
                        .executeQuery()
                        .apply {
                            while (next()) {
                                val projectId = getInt("project_dependency_id")
                                val taskId = getInt("dependant_task_id")
                                val priority = getString("priority").toPriority()

                                project.tasks
                                    .find { task -> task.id == taskId }
                                    ?.dependencies
                                    ?.add(ProjectDependency(projectId, priority))
                            }
                        }
                }
            }

            return project
        }
    }

    override fun projectExists(id: Int): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from project where id = ? limit 1")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    return next()
                }
        }
    }

    private fun getProjectProgress(connection: Connection, projectId: Int): Double {
        connection.prepareStatement("""
            select round((SUM(t.completed)::float / (select count(id) from task t where project_id = ?)::float)::numeric, 3)
    from (select done::float / needed::float as completed from task where project_id = ?) t;
        """.trimIndent())
            .apply { setInt(1, projectId); setInt(2, projectId) }
            .executeQuery()
            .use { rs ->
                if (rs.next()) {
                    return rs.getDouble(1)
                }
            }
        return 0.0
    }

    private fun String.toPriority(): Priority {
        when(this) {
            "NONE" -> return Priority.NONE
            "LOW" -> return Priority.LOW
            "MEDIUM" -> return Priority.MEDIUM
            "HIGH" -> return Priority.HIGH
        }
        return Priority.NONE
    }

    private fun String.toDimension(): Dimension {
        when(this) {
            "OVERWORLD" -> return Dimension.OVERWORLD
            "NETHER" -> return Dimension.NETHER
            "THE_END" -> return Dimension.THE_END
        }
        return Dimension.OVERWORLD
    }

    private fun String.toTaskType(): TaskType {
        when(this) {
            "COUNTABLE" -> return TaskType.COUNTABLE
            "DOABLE" -> return TaskType.DOABLE
        }
        return TaskType.COUNTABLE
    }

    private fun String.toTaskStage(): TaskStage {
        when(this) {
            TaskStages.TODO.id -> return TaskStages.TODO
            TaskStages.IN_PROGRESS.id-> return TaskStages.IN_PROGRESS
            TaskStages.DONE.id -> return TaskStages.DONE
        }
        return TaskStages.TODO
    }
}