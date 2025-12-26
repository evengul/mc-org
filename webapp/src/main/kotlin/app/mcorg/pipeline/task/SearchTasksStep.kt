package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.extractors.toActionTasks

data class SearchTasksInput(
    val query: String? = null,
    val completionStatus: String = "IN_PROGRESS", // ALL, IN_PROGRESS, COMPLETED
    val sortBy: String = "required_amount_desc" // name_asc, lastModified_desc
)

data class SearchTasksStep(val projectId: Int) : Step<SearchTasksInput, AppFailure.DatabaseError, List<ActionTask>> {
    override suspend fun process(input: SearchTasksInput): Result<AppFailure.DatabaseError, List<ActionTask>> {
        val sortBy = when(input.sortBy) {
            "name_asc" -> "t.name ASC, t.updated_at DESC"
            "lastModified_desc" -> "t.updated_at DESC, t.name ASC"
            else -> "t.name ASC, t.updated_at DESC"
        }
        return DatabaseSteps.query<SearchTasksInput, List<ActionTask>>(
            sql = SafeSQL.select("""
                SELECT 
                    t.id,
                    t.project_id,
                    t.name,
                    t.completed
                FROM action_task t
                WHERE t.project_id = ?
                  AND (? IS NULL OR LOWER(t.name) LIKE ?)
                  AND (? = 'ALL'
                    OR (? = 'COMPLETED' AND t.completed = TRUE)
                    OR (? = 'IN_PROGRESS' AND t.completed = FALSE)
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
            resultMapper = { it.toActionTasks() }
        ).process(input)
    }
}