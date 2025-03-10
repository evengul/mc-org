package app.mcorg.infrastructure.repository

import app.mcorg.domain.minecraft.Dimension
import app.mcorg.domain.projects.*
import app.mcorg.domain.users.User
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

    override fun deleteProject(id: Int) {
        getConnection().use {
            it.prepareStatement("delete from project where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun getWorldProjects(id: Int): List<SlimProject> {
        getConnection().use { conn ->
            conn.prepareStatement("select world_id,project.id as project_id,name,priority,dimension,assignee as user_id, u.username as username from project left join users u on project.assignee = u.id where world_id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    val list = mutableListOf<SlimProject>()
                    while (next()) {
                        val projectId = getInt("project_id")
                        val projectName = getString("name")
                        val worldId = getInt("world_id")
                        val priority = getString("priority").toPriority()
                        val dimension = getString("dimension").toDimension()
                        val assigneeId = getInt("user_id").takeIf { it > 0 }
                        val assigneeName = getString("username")
                        val assignee = if(assigneeId != null && assigneeName != null) User(assigneeId, assigneeName) else null
                        val progress = getProjectProgress(conn, projectId)
                        list.add(SlimProject(worldId, projectId, projectName, priority, dimension, assignee, progress))
                    }
                    return list
                }
        }
    }

    override fun createProject(
        worldId: Int,
        name: String,
        dimension: Dimension,
        priority: Priority,
        requiresPerimeter: Boolean
    ): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into project(world_id, name, archived, priority, requires_perimeter, dimension, assignee) values (?, ?, false, ?, ?, ?, null) returning id")
                .apply {
                    setInt(1, worldId)
                    setString(2, name)
                    setString(3, priority.name)
                    setBoolean(4, requiresPerimeter)
                    setString(5, dimension.name)
                }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) return getInt(1)
                }
            }

            throw IllegalStateException("Could not create project")
        }
    }

    override fun changeProjectName(id: Int, name: String) {
        getConnection().use {
            it
                .prepareStatement("update project set name = ? where id = ?", id)
                .apply { setString(1, name); setInt(2, id) }
                .executeUpdate()
        }
    }

    override fun archiveProject(id: Int) {
        getConnection().use {
            it.prepareStatement("update project set archived = true where id = ?", id)
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun openProject(id: Int) {
        getConnection().use {
            it.prepareStatement("update project set archived = false where id = ?", id)
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun getProjectAssignee(id: Int): User? {
        getConnection().use {
            it.prepareStatement("select u.id as user_id, u.username as username from project p join users u on p.assignee = u.id where p.id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        val userId = getInt("user_id")
                        val username = getString("username")
                        if (userId > 0) {
                            return User(userId, username)
                        }
                    }
                }
        }
        return null
    }

    override fun assignProject(id: Int, userId: Int) {
        getConnection().use {
            it.prepareStatement("update project set assignee = ? where id = ?")
                .apply { setInt(1, userId); setInt(2, id) }
                .executeUpdate()
        }
    }

    override fun removeProjectAssignment(id: Int) {
        getConnection().use {
            it.prepareStatement("update project set assignee = null where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into task (project_id, name, needed, done, type) values (?, ?, ?, 0, 'COUNTABLE') returning id")
                .apply { setInt(1, projectId); setString(2, name); setInt(3, needed) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) return getInt(1)
                }
            }
        }

        throw IllegalStateException("Could not create countable task")
    }

    override fun addDoableTask(projectId: Int, name: String, priority: Priority): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into task (project_id, name, needed, done, type) values (?, ?, 1, 0, 'DOABLE') returning id")
                .apply { setInt(1, projectId); setString(2, name); }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) return getInt(1)
                }
            }
        }

        throw IllegalStateException("Could not create doable task")
    }

    override fun removeTask(id: Int) {
        getConnection().use {
            it.prepareStatement("delete from task where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun completeTask(id: Int) {
        getConnection().use {
            it.prepareStatement("update task set done = needed where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun undoCompleteTask(id: Int) {
        getConnection().use {
            it.prepareStatement("update task set done = 0 where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun updateTaskStage(id: Int, stage: TaskStage) {
        getConnection().use {
            it.prepareStatement("update task set stage = ? where id = ?")
                .apply { setString(1, stage.id); setInt(2, id) }
                .executeUpdate()
        }
    }

    override fun updateCountableTask(id: Int, needed: Int, done: Int) {
        getConnection().use {
            it.prepareStatement("update task set done = ?, needed = ? where id = ?")
                .apply { setInt(1, done); setInt(2, needed); setInt(3, id) }
                .executeUpdate()
        }
    }

    override fun taskRequiresMore(id: Int, needed: Int) {
        getConnection().use {
            it.prepareStatement("update task set needed = ? where id = ?")
                .apply { setInt(1, needed); setInt(2, id) }
                .executeUpdate()
        }
    }

    override fun taskDoneMore(id: Int, doneMore: Int) {
        getConnection().use {
            val (needed, done) = getTaskProgress(it, id)
            val value = needed.coerceAtMost(done + doneMore)
            it.prepareStatement("update task set done = ? where id = ?")
                .apply { setInt(1, value); setInt(2, id) }
                .executeUpdate()
        }
    }

    override fun editTaskRequirements(id: Int, needed: Int, done: Int) {
        getConnection().use {
            it.prepareStatement("update task set needed = ?, done = ? where id = ?")
                .apply { setInt(1, needed); setInt(2, done); setInt(3, id) }
                .executeUpdate()
        }
    }

    override fun assignTask(id: Int, userId: Int) {
        getConnection().use {
            it.prepareStatement("update task set assignee = ? where id = ?")
                .apply { setInt(1, userId); setInt(2, id); }
                .executeUpdate()
        }
    }

    override fun removeTaskAssignment(id: Int) {
        getConnection().use {
            it.prepareStatement("update task set assignee = null where id = ?")
                .apply { setInt(1, id); }
                .executeUpdate()
        }
    }

    override fun removeUserAssignments(id: Int) {
        getConnection().use {
            it.prepareStatement("update task set assignee = null where assignee = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
            it.prepareStatement("update project set assignee = null where assignee = ?")
                .apply { setInt(1, id); }
                .executeUpdate()
        }
    }

    override fun getTaskAssignee(id: Int): User? {
        getConnection().use {
            it.prepareStatement("select u.id as user_id, u.username as username from task t join users u on t.assignee = u.id where t.id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        val userId = getInt("user_id")
                        val username = getString("username")
                        if (userId > 0) {
                            return User(userId, username)
                        }
                    }
                }
        }
        return null
    }

    override fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int {
        getConnection().use {
            val statement = getConnection()
                .prepareStatement("insert into project_dependency (dependant_task_id, project_dependency_id, priority) values (?, ?, ?) returning id")
                .apply { setInt(1, taskId); setInt(2, projectId); setString(3, priority.name) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) return getInt(1)
                }
            }
        }

        throw IllegalStateException("Could not create project dependency")
    }

    override fun removeProjectDependencyToTask(dependencyId: Int) {
        getConnection().use {
            it.prepareStatement("delete from project_dependency where id = ?")
                .apply { setInt(1, dependencyId) }
                .executeUpdate()
        }
    }

    data class TaskProgress(val needed: Int, val done: Int)

    private fun getTaskProgress(connection: Connection, id: Int): TaskProgress {
        connection.prepareStatement("select needed, done from task where id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                if (next()) {
                    return TaskProgress(getInt("needed"), getInt("done"))
                }
            }
        throw IllegalStateException("Could not find task progress with id $id")
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