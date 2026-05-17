package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class UpdateProjectStageStep(val projectId: Int) : Step<ProjectStage, AppFailure.DatabaseError, ProjectStage> {
    override suspend fun process(input: ProjectStage): Result<AppFailure.DatabaseError, ProjectStage> {
        return DatabaseSteps.update<ProjectStage>(
            sql = SafeSQL.update("""
                UPDATE projects
                SET stage = ?, updated_at = NOW()
                WHERE id = ?
            """),
            parameterSetter = { statement, stage ->
                statement.setString(1, stage.name)
                statement.setInt(2, projectId)
            }
        ).process(input).map { input }
    }
}
