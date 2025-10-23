package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.CreateProjectFailures
import app.mcorg.pipeline.world.toProject

object GetProjectAfterCreation : Step<Int, CreateProjectFailures, Project> {
    override suspend fun process(input: Int): Result<CreateProjectFailures, Project> {
        return DatabaseSteps.query<Int, CreateProjectFailures, Project?>(
            getProjectByIdQuery,
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            errorMapper = { CreateProjectFailures.DatabaseError },
            resultMapper = {
                if (it.next()) {
                    it.toProject()
                } else {
                    null
                }
            }
        ).process(input).flatMap {
            if (it != null) {
                Result.success(it)
            } else {
                Result.Failure(CreateProjectFailures.DatabaseError)
            }
        }
    }
}
