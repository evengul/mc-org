package app.mcorg.pipeline.project

import app.mcorg.pipeline.project.commonsteps.GetProjectListStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.projectListPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProjectList() {
    val user = getUser()
    val worldId = getWorldId()

    handlePipeline(
        onSuccess = { (world, _, projects) ->
            respondHtml(projectListPage(user, world, projects))
        }
    ) {
        val world = GetWorldStep.run(worldId)
        val worldMember = GetWorldMemberStep(worldId).run(user.id)
        val projects = GetProjectListStep(worldId).run(Unit)
        Triple(world, worldMember, projects)
    }
}
