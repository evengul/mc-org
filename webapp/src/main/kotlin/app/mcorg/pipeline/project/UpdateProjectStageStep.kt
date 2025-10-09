package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.UpdateProjectStageFailures
import java.time.ZonedDateTime

object UpdateProjectStageStep : Step<UpdateProjectStageInput, UpdateProjectStageFailures, UpdateProjectStageOutput> {
    override suspend fun process(input: UpdateProjectStageInput): Result<UpdateProjectStageFailures, UpdateProjectStageOutput> {
        return DatabaseSteps.transaction(
            step = object : Step<UpdateProjectStageInput, UpdateProjectStageFailures, UpdateProjectStageOutput> {
                override suspend fun process(transactionInput: UpdateProjectStageInput): Result<UpdateProjectStageFailures, UpdateProjectStageOutput> {
                    // First, get the current stage
                    val currentStageResult = DatabaseSteps.query<UpdateProjectStageInput, UpdateProjectStageFailures, ProjectStage>(
                        sql = SafeSQL.select("""
                            SELECT stage 
                            FROM projects 
                            WHERE id = ?
                        """),
                        parameterSetter = { statement, queryInput ->
                            statement.setInt(1, queryInput.projectId)
                        },
                        errorMapper = { UpdateProjectStageFailures.DatabaseError },
                        resultMapper = { resultSet ->
                            if (resultSet.next()) {
                                ProjectStage.valueOf(resultSet.getString("stage"))
                            } else {
                                throw RuntimeException("Project not found")
                            }
                        }
                    ).process(transactionInput)

                    when (currentStageResult) {
                        is Result.Failure -> return currentStageResult
                        is Result.Success -> {
                            val previousStage = currentStageResult.value
                            val updatedAt = ZonedDateTime.now()

                            // Update the project stage
                            val updateResult = DatabaseSteps.update<UpdateProjectStageInput, UpdateProjectStageFailures>(
                                sql = SafeSQL.update("""
                                    UPDATE projects 
                                    SET stage = ?, updated_at = ?
                                    WHERE id = ?
                                """),
                                parameterSetter = { statement, updateInput ->
                                    statement.setString(1, updateInput.newStage.name)
                                    statement.setTimestamp(2, java.sql.Timestamp.from(updatedAt.toInstant()))
                                    statement.setInt(3, updateInput.projectId)
                                },
                                errorMapper = { UpdateProjectStageFailures.DatabaseError }
                            ).process(transactionInput)

                            when (updateResult) {
                                is Result.Failure -> return updateResult
                                is Result.Success -> {
                                    // Record the stage change in history
                                    val historyResult = DatabaseSteps.update<UpdateProjectStageInput, UpdateProjectStageFailures>(
                                        sql = SafeSQL.insert("""
                                            INSERT INTO project_stage_changes (project_id, stage, changed_at)
                                            VALUES (?, ?, ?)
                                        """),
                                        parameterSetter = { statement, historyInput ->
                                            statement.setInt(1, historyInput.projectId)
                                            statement.setString(2, historyInput.newStage.name)
                                            statement.setTimestamp(3, java.sql.Timestamp.from(updatedAt.toInstant()))
                                        },
                                        errorMapper = { UpdateProjectStageFailures.DatabaseError }
                                    ).process(transactionInput)

                                    return when (historyResult) {
                                        is Result.Failure -> historyResult
                                        is Result.Success -> Result.success(
                                            UpdateProjectStageOutput(
                                                projectId = transactionInput.projectId,
                                                previousStage = previousStage,
                                                newStage = transactionInput.newStage,
                                                updatedAt = updatedAt
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            errorMapper = { UpdateProjectStageFailures.DatabaseError }
        ).process(input)
    }
}
