package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.world.toProject

sealed interface GetProjectByIdFailures {
    data object ProjectNotFound : GetProjectByIdFailures
    data object AccessDenied : GetProjectByIdFailures
    data object DatabaseError : GetProjectByIdFailures
}

object GetProjectByIdStep : Step<Int, GetProjectByIdFailures, Project> {
    override suspend fun process(input: Int): Result<GetProjectByIdFailures, Project> {
        val projectStep = DatabaseSteps.query<Int, GetProjectByIdFailures, Project?>(
            sql = getProjectByIdQuery,
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            errorMapper = { GetProjectByIdFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.toProject()
                } else {
                    null
                }
            }
        )

        return when (val projectResult = projectStep.process(input)) {
            is Result.Success -> {
                val project = projectResult.getOrNull()
                if (project != null) {
                    Result.success(project)
                } else {
                    Result.failure(GetProjectByIdFailures.ProjectNotFound)
                }
            }
            is Result.Failure -> projectResult
        }
    }
}
