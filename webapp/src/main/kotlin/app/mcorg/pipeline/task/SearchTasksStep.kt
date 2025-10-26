package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure

object SearchTasksStep : Step<SearchTasksInput, SearchTasksFailures.DatabaseError, SearchTasksResult> {
    override suspend fun process(input: SearchTasksInput): Result<SearchTasksFailures.DatabaseError, SearchTasksResult> {
        val sortBy = when(input.sortBy) {
            "name_asc" -> "t.name ASC, priority_order ASC, t.updated_at DESC"
            "lastModified_desc" -> "t.updated_at DESC, priority_order ASC, t.name ASC"
            "priority_asc" -> "priority_order ASC, t.updated_at DESC, t.name ASC"
            else -> "priority_order ASC, t.updated_at DESC, t.name ASC"
        }
        val taskStep = DatabaseSteps.query<SearchTasksInput, SearchTasksFailures.DatabaseError, List<Task>>(
            sql = searchTasksQuery(sortBy),
            parameterSetter = { statement, searchInput ->
                statement.setInt(1, searchInput.projectId)

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
            errorMapper = { _: DatabaseFailure -> SearchTasksFailures.DatabaseError },
            resultMapper = { it.toTasks() }
        )

        return when (val result = taskStep.process(input)) {
            is Result.Success -> Result.Success(SearchTasksResult(result.value))
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}