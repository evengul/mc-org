package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.model.projects.SlimProject
import app.mcorg.domain.model.projects.matches
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetSpecifiedProjectsStepFailure
import app.mcorg.pipeline.project.ProjectEnumMappers.toDimension
import app.mcorg.pipeline.project.ProjectEnumMappers.toPriority
import app.mcorg.pipeline.useConnection

data class GetSpecifiedProjectsStep(val worldId: Int) : Step<ProjectSpecification, GetSpecifiedProjectsStepFailure, Pair<Int, List<SlimProject>>> {
    override suspend fun process(input: ProjectSpecification): Result<GetSpecifiedProjectsStepFailure, Pair<Int, List<SlimProject>>> {
        val list = mutableListOf<SlimProject>()
        val projects = useConnection {
            prepareStatement("select world_id,project.id as project_id,name,priority,dimension,assignee as user_id, u.username as username from project left join users u on project.assignee = u.id where world_id = ?")
                .apply { setInt(1, worldId) }
                .executeQuery()
                .apply {
                    while (next()) {
                        val projectId = getInt("project_id")
                        val projectName = getString("name")
                        val worldId = getInt("world_id")
                        val priority = getString("priority").toPriority()
                        val dimension = getString("dimension").toDimension()
                        val assigneeId = getInt("user_id").takeIf { it > 0 }
                        val assigneeName = getString("username")
                        val assignee = if (assigneeId != null && assigneeName != null) User(assigneeId, assigneeName) else null
                        val progress = getProjectProgress(this@useConnection, projectId)
                        list.add(SlimProject(worldId, projectId, projectName, priority, dimension, assignee, progress))
                    }
                }
            return@useConnection Result.success(list)
        }

        return when (projects) {
            is Result.Failure -> Result.failure(GetSpecifiedProjectsStepFailure.Other(projects.error))
            is Result.Success -> {
                Result.success(projects.value.size to projects.value.filter { it.matches(input) })
            }
        }
    }
}