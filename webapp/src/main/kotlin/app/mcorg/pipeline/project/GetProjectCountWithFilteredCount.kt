package app.mcorg.pipeline.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface GetProjectCountWithFilteredCountFailure : CreateProjectFailure, DeleteProjectFailure {
    data class Other(val failure: DatabaseFailure) : GetProjectCountWithFilteredCountFailure
}

data class GetProjectCountWithFilteredCount(val worldId: Int) : Step<ProjectSpecification, GetProjectCountWithFilteredCountFailure, Pair<Int, Int>> {
    override suspend fun process(input: ProjectSpecification): Result<GetProjectCountWithFilteredCountFailure, Pair<Int, Int>> {
        return useConnection({ GetProjectCountWithFilteredCountFailure.Other(it) }) {
            val total = prepareStatement("select count(*) from project where world_id = ?")
                .apply { setInt(1, worldId) }
                .executeQuery()
                .use { if (it.next()) it.getInt(1) else 0 }

            val filtered = prepareStatement("""
                select count(*) as filtered_count
                from (
                         select distinct project.id, project.world_id, project.name, users.username, coalesce(done_count::float / nullif(total_count, 0), 0) as progress
                         from project
                                  left join users on project.assignee = users.id
                                  left join task on project.id = task.project_id
                                  left join (
                             select project_id, count(*) as total_count,
                                    sum(case when task.stage = 'DONE' then 1 else 0 end) as done_count
                             from task
                             group by project_id
                         ) task_progress on project.id = task_progress.project_id
                         where (project.name ilike '%' || ? || '%' or users.username ilike '%' || ? || '%') and (not ? or task_progress.done_count >= task_progress.total_count) and project.world_id = ?
                ) as filtered_projects;
            """.trimIndent())
                .apply {
                    setString(1, input.search ?: "")
                    setString(2, input.search ?: "")
                    setBoolean(3, input.hideCompleted)
                    setInt(4, worldId)
                }
                .executeQuery()
                .use { if (it.next()) it.getInt(1) else 0 }

            return@useConnection Result.success(total to filtered)
        }
    }
}
