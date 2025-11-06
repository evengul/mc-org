package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.extractors.toProjects

data class SearchProjectsInput(
    val worldId: Int,
    val query: String = "",
    val sortBy: String = "lastModified_desc",
    val showCompleted: Boolean = false
)

object SearchProjectsStep : Step<SearchProjectsInput, AppFailure.DatabaseError, List<Project>> {
    override suspend fun process(input: SearchProjectsInput): Result<AppFailure.DatabaseError, List<Project>> {
        val sortQuery = when(input.sortBy) {
            "name_asc" -> "p.name ASC, p.updated_at DESC"
            "lastModified_desc" -> "p.updated_at DESC, p.name ASC"
            else -> "p.updated_at DESC, p.name ASC"
        }
        return DatabaseSteps.query<SearchProjectsInput, List<Project>>(
            getProjectsByWorldIdQuery(sortQuery),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, searchInput.worldId)

                val normalizedQuery = searchInput.query.trim().lowercase()
                statement.setString(2, normalizedQuery)
                statement.setString(3, normalizedQuery)
                statement.setString(4, normalizedQuery)

                statement.setBoolean(5, searchInput.showCompleted)
            },
            resultMapper = { it.toProjects() }
        ).process(input)
    }

    private fun getProjectsByWorldIdQuery(sortBy: String = "p.updated_at DESC, p.name ASC") = SafeSQL.select("""
        SELECT
            p.id,
            p.world_id,
            p.name,
            p.description,
            p.type,
            p.stage,
            p.location_dimension,
            p.location_x,
            p.location_y,
            p.location_z,
            p.created_at,
            p.updated_at,
            COALESCE(task_stats.tasks_total, 0) as tasks_total,
            COALESCE(task_stats.tasks_completed, 0) as tasks_completed
        FROM projects p
        LEFT JOIN (
            SELECT 
                t.project_id,
                COUNT(t.id) as tasks_total,
                COUNT(ct.id) as tasks_completed
            FROM tasks t
            LEFT JOIN (
                SELECT t2.id
                FROM tasks t2
                GROUP BY t2.id
                HAVING COUNT(*) = SUM(
                    CASE
                        WHEN t2.requirement_type = 'ITEM' AND t2.requirement_item_collected >= t2.requirement_item_required_amount THEN 1
                        WHEN t2.requirement_type = 'ACTION' AND t2.requirement_action_completed = true THEN 1
                        ELSE 0
                    END
                )
            ) ct ON t.id = ct.id
            GROUP BY t.project_id
        ) task_stats ON p.id = task_stats.project_id
        WHERE p.world_id = ? AND (? = '' OR LOWER(p.name) ILIKE '%' || ? || '%' OR LOWER(p.description) ILIKE '%' || ? || '%') AND (? = TRUE OR p.stage != 'COMPLETED')
        ORDER BY $sortBy 
    """.trimIndent())
}