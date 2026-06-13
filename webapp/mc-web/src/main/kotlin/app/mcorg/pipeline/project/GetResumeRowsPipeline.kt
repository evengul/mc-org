package app.mcorg.pipeline.project

import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.GetResumeProjectIdStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.ResumeHeroData
import app.mcorg.presentation.templated.dsl.ResumeSort
import app.mcorg.presentation.templated.dsl.resumeHeroRowsFragment
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

/**
 * Re-renders the resume hero's resource rows with a different sort —
 * the HTMX target of the hero's sorter pills.
 */
suspend fun ApplicationCall.handleGetResumeRows() {
    val worldId = getWorldId()
    val sort = ResumeSort.fromParam(request.queryParameters["sort"])

    handlePipeline(
        onSuccess = { resume ->
            if (resume != null) {
                respondHtml(resumeHeroRowsFragment(worldId, resume, sort))
            } else {
                respondHtml(createHTML().div { id = "fl-resume-rows" })
            }
        }
    ) {
        val resumeId = GetResumeProjectIdStep(worldId).run(Unit)
        if (resumeId != null) {
            val project = GetProjectByIdStep.run(resumeId)
            val resources = GetAllResourceGatheringItemsStep.run(resumeId)
            ResumeHeroData(project, resources)
        } else null
    }
}
