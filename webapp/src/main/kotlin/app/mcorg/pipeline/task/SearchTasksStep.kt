package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.extractors.toTasks

data class SearchTasksInput(
    val query: String? = null,
    val completionStatus: String = "IN_PROGRESS", // ALL, IN_PROGRESS, COMPLETED
    val priority: String = "ALL", // ALL or Priority enum name
    val stage: String = "ALL",
    val sortBy: String = "lastModified_desc" // name_asc, lastModified_desc
)

data class SearchTasksStep(val projectId: Int) : Step<SearchTasksInput, AppFailure.DatabaseError, List<Task>> {
    override suspend fun process(input: SearchTasksInput): Result<AppFailure.DatabaseError, List<Task>> {
        val sortBy = when(input.sortBy) {
            "name_asc" -> "t.name ASC, priority_order ASC, t.updated_at DESC"
            "lastModified_desc" -> "t.updated_at DESC, priority_order ASC, t.name ASC"
            "priority_asc" -> "priority_order ASC, t.updated_at DESC, t.name ASC"
            else -> "priority_order ASC, t.updated_at DESC, t.name ASC"
        }
        return DatabaseSteps.query<SearchTasksInput, List<Task>>(
            sql = SafeSQL.select("""
                SELECT 
                    t.id,
                    t.project_id,
                    t.name,
                    t.description,
                    t.stage,
                    t.priority,
                    t.requirement_type,
                    t.item_id,
                    t.requirement_item_required_amount,
                    t.requirement_item_collected,
                    t.requirement_action_completed,
                    pd.depends_on_project_id AS solved_by_project_id,
                    p.name AS solved_by_project_name,
                    CASE 
                        WHEN t.priority = 'CRITICAL' THEN 1
                        WHEN t.priority = 'HIGH' THEN 2
                        WHEN t.priority = 'MEDIUM' THEN 3
                        WHEN t.priority = 'LOW' THEN 4
                        ELSE 5
                    END as priority_order
                FROM tasks t
                LEFT JOIN project_dependencies pd on t.id = ANY(pd.tasks_depending_on_dependency_project)
                LEFT JOIN projects p on pd.depends_on_project_id = p.id
                WHERE t.project_id = ?
                  AND (? IS NULL OR LOWER(t.name) LIKE ? OR LOWER(t.description) LIKE ?)
                  AND (? = 'ALL' OR t.priority = ?)
                  AND (? = 'ALL' OR t.stage = ?)
                  AND (? = 'ALL'
                    OR (? = 'COMPLETED' AND ((t.requirement_type = 'ACTION' AND t.requirement_action_completed = TRUE) OR (t.requirement_type = 'ITEM' AND t.requirement_item_collected >= t.requirement_item_required_amount)))
                    OR (? = 'IN_PROGRESS' AND ((t.requirement_type = 'ACTION' AND t.requirement_action_completed = FALSE) OR (t.requirement_type = 'ITEM' AND t.requirement_item_collected < t.requirement_item_required_amount)))
                    )
                ORDER BY $sortBy
            """.trimIndent()),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, projectId)

                val searchPattern = searchInput.query?.let { "%$it%".lowercase() }
                statement.setString(2, searchInput.query)
                statement.setString(3, searchPattern)
                statement.setString(4, searchPattern)

                statement.setString(5, searchInput.priority)
                statement.setString(6, searchInput.priority)

                statement.setString(7, searchInput.stage)
                statement.setString(8, searchInput.stage)

                statement.setString(9, searchInput.completionStatus)
                statement.setString(10, searchInput.completionStatus)
                statement.setString(11, searchInput.completionStatus)
            },
            resultMapper = { it.toTasks() }
        ).process(input)
    }
}