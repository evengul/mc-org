package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.model.projects.SlimProject
import app.mcorg.domain.model.projects.matches
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.project.ProjectEnumMappers.toDimension
import app.mcorg.pipeline.project.ProjectEnumMappers.toPriority
import app.mcorg.pipeline.useConnection
import app.mcorg.presentation.handler.GetProjectsData

sealed interface GetSpecifiedProjectsStepFailure : GetProjectsFailure {
    data class Other(val failure: DatabaseFailure) : GetSpecifiedProjectsStepFailure
}

object GetSpecifiedProjectsStep : Step<GetProjectsData, GetSpecifiedProjectsStepFailure, GetProjectsData> {
    override suspend fun process(input: GetProjectsData): Result<GetSpecifiedProjectsStepFailure, GetProjectsData> {
        val list = mutableListOf<SlimProject>()
        val projects = useConnection {
            prepareStatement("select world_id,project.id as project_id,name,priority,dimension,assignee as user_id, u.username as username from project left join users u on project.assignee = u.id where world_id = ?")
                .apply { setInt(1, input.worldId) }
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
                Result.success(input.copy(
                    projects = projects.value.filter { it.matches(input.specification) },
                    totalProjectCount = projects.value.size
                ))
            }
        }
    }
}