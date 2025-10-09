package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.CreateProjectFailures
import app.mcorg.pipeline.world.toProjects

object GetProjectsForWorldStep : Step<Int, CreateProjectFailures, List<Project>> {
    override suspend fun process(input: Int): Result<CreateProjectFailures, List<Project>> {
        return DatabaseSteps.query<Int, CreateProjectFailures, List<Project>>(
            getProjectsByWorldIdQuery,
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
            },
            errorMapper = { CreateProjectFailures.DatabaseError },
            resultMapper = { it.toProjects() }
        ).process(input)
    }
}
