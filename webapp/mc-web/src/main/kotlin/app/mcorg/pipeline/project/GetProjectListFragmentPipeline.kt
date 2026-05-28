package app.mcorg.pipeline.project

import app.mcorg.pipeline.project.commonsteps.GetProjectListStep
import app.mcorg.pipeline.project.commonsteps.GetProjectPlanListStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.projectsViewFragment
import app.mcorg.presentation.templated.dsl.pages.projectsViewFragmentPlan
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProjectListFragment() {
    val user = getUser()
    val worldId = getWorldId()
    val view = request.queryParameters["view"]?.takeIf { it == "plan" } ?: "execute"

    if (view == "plan") {
        handlePipeline(
            onSuccess = { (world, _, projects) ->
                respondHtml(projectsViewFragmentPlan(world, projects))
            }
        ) {
            val world = GetWorldStep.run(worldId)
            val worldMember = GetWorldMemberStep(worldId).run(user.id)
            val projects = GetProjectPlanListStep(worldId).run(Unit)
            Triple(world, worldMember, projects)
        }
    } else {
        handlePipeline(
            onSuccess = { (world, _, projects) ->
                respondHtml(projectsViewFragment(world, projects, view))
            }
        ) {
            val world = GetWorldStep.run(worldId)
            val worldMember = GetWorldMemberStep(worldId).run(user.id)
            val projects = GetProjectListStep(worldId).run(Unit)
            Triple(world, worldMember, projects)
        }
    }
}
