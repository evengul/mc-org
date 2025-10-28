package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.hxTarget
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.getStageSelectFragment() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()


    executePipeline(
        onSuccess = { selectedStage ->
            respondHtml(createHTML().select {
                id = "project-stage-selector"
                name = "stage"

                // HTMX attributes for dynamic stage updates
                hxPatch(Link.Worlds.world(worldId).project(projectId).to + "/stage")
                hxTarget("#project-stage-selector")
                hxSwap("outerHTML")
                hxTrigger("change changed")

                ProjectStage.entries.forEach {
                    option {
                        value = it.name
                        if (it == selectedStage) {
                            selected = true
                        }
                        +it.toPrettyEnumName()
                    }
                }
            })
        },
        onFailure = {
            hxTarget("none")
            respondHtml(createHTML().div {
                hxOutOfBands("true")
                chipComponent {
                    id = "project-stage-chip"
                    variant = ChipVariant.ACTION
                    + "Unknown Stage"
                }
            })
        }
    ) {
        step(Step.value(projectId))
            .step(GetProjectStageStep)
    }
}

private object GetProjectStageStep : Step<Int, DatabaseFailure, ProjectStage> {
    override suspend fun process(input: Int): Result<DatabaseFailure, ProjectStage> {
        return DatabaseSteps.query<Int, DatabaseFailure, ProjectStage?>(
            sql = SafeSQL.select("SELECT stage FROM projects WHERE id = ?"),
            parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
            errorMapper = { it },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    val stageString = resultSet.getString("stage")
                    ProjectStage.valueOf(stageString)
                } else {
                    null
                }
            }
        ).process(input).flatMap {
            if (it == null) Result.failure(DatabaseFailure.NotFound)
            Result.success(it!!)
        }
    }
}