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
    val sortBy: String = "required_amount_desc" // name_asc, lastModified_desc
)

data class SearchTasksStep(val projectId: Int) : Step<SearchTasksInput, AppFailure.DatabaseError, List<Task>> {
    override suspend fun process(input: SearchTasksInput): Result<AppFailure.DatabaseError, List<Task>> {
        val sortBy = when(input.sortBy) {
            "name_asc" -> "t.name ASC, required_amount DESC, t.updated_at DESC"
            "lastModified_desc" -> "t.updated_at DESC, required_amount DESC, t.name ASC"
            "required_amount_desc" -> "required_amount DESC, t.updated_at DESC, t.name ASC"
            else -> "required_amount DESC, t.updated_at DESC, t.name ASC"
        }
        return DatabaseSteps.query<SearchTasksInput, List<Task>>(
            sql = SafeSQL.select("""
                SELECT 
                    t.id,
                    t.project_id,
                    t.name,
                    t.requirement_type,
                    t.item_id,
                    t.requirement_item_required_amount,
                    t.requirement_item_collected,
                    t.requirement_action_completed,
                    CASE 
                        WHEN t.requirement_type = 'ACTION' THEN 1
                        ELSE t.requirement_item_required_amount
                    END AS required_amount,
                    pd.depends_on_project_id AS solved_by_project_id,
                    p.name AS solved_by_project_name
                FROM tasks t
                LEFT JOIN project_dependencies pd on t.id = ANY(pd.tasks_depending_on_dependency_project)
                LEFT JOIN projects p on pd.depends_on_project_id = p.id
                WHERE t.project_id = ?
                  AND (? IS NULL OR LOWER(t.name) LIKE ?)
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

                statement.setString(4, searchInput.completionStatus)
                statement.setString(5, searchInput.completionStatus)
                statement.setString(6, searchInput.completionStatus)
            },
            resultMapper = { it.toTasks() }
        ).process(input)
    }
}