package no.mcorg.infrastructure.repository

import no.mcorg.domain.*

class ProjectsImpl(config: AppConfiguration) : Projects, Repository(config) {
    override fun getProject(id: Int, includeTasks: Boolean, includeDependencies: Boolean): Project? {
        val project = getConnection()
            .prepareStatement("SELECT world_id, team_id, id, name, archived FROM project WHERE id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .let {
                if (!it.next()) return@let null
                else return@let Project(
                    worldId = it.getInt("world_id"),
                    teamId = it.getInt("team_id"),
                    id = it.getInt("id"),
                    name = it.getString("name"),
                    archived = it.getBoolean("archived"),
                    tasks = mutableListOf()
                )
            }

        if (project == null) return null

        if (includeTasks) {

            getConnection()
                .prepareStatement("select id,name,needed,done from task where project_id = ?")
                .apply { setInt(1, project.id) }
                .executeQuery()
                .apply {
                    while (next()) {
                        project.tasks.add(
                            Task(
                                id = getInt("id"),
                                name = getString("name"),
                                needed = getInt("needed"),
                                done = getInt("done"),
                                priority = getString("priority").toPriority(),
                                dependencies = mutableListOf()
                            )
                        )
                    }
                }

            if (includeDependencies) {
                getConnection()
                    .prepareStatement("select d.id, dependant_task_id, project_dependency_id, d.priority as priority from project_dependency d join task t on t.id = d.dependant_task_id where t.project_id = ?")
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

    override fun deleteProject(id: Int) {
        getConnection()
            .prepareStatement("delete from project where id = ?")
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun getTeamProjects(id: Int): List<SlimProject> {
        getConnection()
            .prepareStatement("select p.world_id, p.team_id, p.id, p.name from project p join team t on p.team_id = t.id where t.id = ? and archived = false")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                val projects = mutableListOf<SlimProject>()
                while (next()) {
                    projects.add(
                        SlimProject(
                            worldId = getInt("world_id"),
                            teamId = getInt("team_id"),
                            id = getInt("id"),
                            name = getString("name"),
                        )
                    )
                }
                return projects
            }
    }

    override fun createProject(worldId: Int, teamId: Int, name: String): Int {
        val statement = getConnection()
            .prepareStatement("insert into project(world_id, team_id, name, archived) values (?, ?, ?, false) returning id")
            .apply { setInt(1, worldId); setInt(2, teamId); setString(3, name) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) return getInt(1)
            }
        }

        throw IllegalStateException("Could not create project")
    }

    override fun getUserProjects(username: String): List<Project> {
        TODO("Not yet implemented")
    }

    override fun changeProjectName(id: Int, name: String) {
        getConnection()
            .prepareStatement("update project set name = ? where id = ?", id)
            .apply { setString(1, name); setInt(2, id) }
            .executeUpdate()
    }

    override fun archiveProject(id: Int) {
        getConnection()
            .prepareStatement("update project set archived = true where id = ?", id)
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun openProject(id: Int) {
        getConnection()
            .prepareStatement("update project set archived = false where id = ?", id)
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int {
        val statement = getConnection()
            .prepareStatement("insert into task (project_id, name, needed, done) values (?, ?, ?, 0) returning id")
            .apply { setInt(1, projectId); setString(2, name); setInt(3, needed) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) return getInt(1)
            }
        }

        throw IllegalStateException("Could not create countable task")
    }

    override fun addDoableTask(projectId: Int, name: String, priority: Priority): Int {
        val statement = getConnection()
            .prepareStatement("insert into task (project_id, name, needed, done) values (?, ?, 1, 0) returning id")
            .apply { setInt(1, projectId); setString(2, name); }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) return getInt(1)
            }
        }

        throw IllegalStateException("Could not create doable task")
    }

    override fun removeTask(id: Int) {
        getConnection()
            .prepareStatement("delete from task where id = ?")
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun completeTask(id: Int) {
        getConnection()
            .prepareStatement("update task set done = needed where id = ?")
            .apply { setInt(1, id) }
            .executeUpdate()
    }

    override fun taskRequiresMore(id: Int, needed: Int) {
        getConnection()
            .prepareStatement("update task set needed = ? where id = ?")
            .apply { setInt(1, needed); setInt(2, id) }
            .executeUpdate()
    }

    override fun taskDoneMore(id: Int, done: Int) {
        getConnection()
            .prepareStatement("update task set done = ? where id = ?")
            .apply { setInt(1, done); setInt(2, id) }
            .executeUpdate()
    }

    override fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int {
        val statement = getConnection()
            .prepareStatement("insert into project_dependency (dependant_task_id, project_dependency_id, priority) values (?, ?, ?) returning id")
            .apply { setInt(1, taskId); setInt(2, projectId); setString(3, priority.name) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) return getInt(1)
            }
        }

        throw IllegalStateException("Could not create project dependency")
    }

    override fun removeProjectDependencyToTask(dependencyId: Int) {
        getConnection()
            .prepareStatement("delete from project_dependency where id = ?")
            .apply { setInt(1, dependencyId) }
            .executeUpdate()
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
}