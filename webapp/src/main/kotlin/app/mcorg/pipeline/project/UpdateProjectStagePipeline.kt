package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateProjectStage() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    executePipeline(
        onSuccess = { result ->
            respondHtml(createHTML().div {
                chipComponent {
                    id = "project-stage-chip"
                    variant = ChipVariant.ACTION
                    hxEditableFromHref = Link.Worlds.world(worldId).project(projectId).to + "/stage-select-fragment"
                    + result.toPrettyEnumName()
                }
            })
        }
    ) {
        value(parameters)
            .step(ValidateStageTransitionStep)
            .step(UpdateProjectStageStep(projectId))
    }
}

object ValidateStageTransitionStep : Step<Parameters, AppFailure.ValidationError, ProjectStage> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ProjectStage> {
        val stageParam = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "stage",
            "Invalid project stage",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { stage ->
                !stage.isNullOrBlank() && runCatching {
                    ProjectStage.valueOf(stage.uppercase())
                }.isSuccess
            }
        ).process(input["stage"])

        return when (stageParam) {
            is Result.Failure -> stageParam
            is Result.Success -> {
                val stage = ProjectStage.valueOf(stageParam.value!!.uppercase())
                Result.success(stage)
            }
        }
    }
}

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
