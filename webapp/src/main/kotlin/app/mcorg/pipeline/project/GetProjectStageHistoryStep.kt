package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectStageChange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class GetProjectStageHistoryInput(
    val projectId: Int
)

sealed interface GetProjectStageHistoryFailures {
    data object DatabaseError : GetProjectStageHistoryFailures
}

object GetProjectStageHistoryStep : Step<GetProjectStageHistoryInput, GetProjectStageHistoryFailures, List<ProjectStageChange>> {
    override suspend fun process(input: GetProjectStageHistoryInput): Result<GetProjectStageHistoryFailures, List<ProjectStageChange>> {
        return DatabaseSteps.query<GetProjectStageHistoryInput, GetProjectStageHistoryFailures, List<ProjectStageChange>>(
            sql = SafeSQL.select("""
                SELECT 
                    project_id,
                    stage,
                    changed_at
                FROM project_stage_changes
                WHERE project_id = ?
                ORDER BY changed_at DESC
            """),
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
            },
            errorMapper = { GetProjectStageHistoryFailures.DatabaseError },
            resultMapper = { resultSet ->
                val stageChanges = mutableListOf<ProjectStageChange>()
                while (resultSet.next()) {
                    stageChanges.add(
                        ProjectStageChange(
                            projectId = resultSet.getInt("project_id"),
                            stage = ProjectStage.valueOf(resultSet.getString("stage")),
                            relatedTasks = emptyList(), // Not stored in database yet
                            enteredOn = resultSet.getTimestamp("changed_at").toInstant().atZone(java.time.ZoneOffset.UTC)
                        )
                    )
                }
                stageChanges
            }
        ).process(input)
    }
}
