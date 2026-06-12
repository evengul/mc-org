package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.project.commonsteps.GetProjectEdgesStep
import app.mcorg.pipeline.project.commonsteps.GetProjectListStep
import app.mcorg.pipeline.project.commonsteps.GetProjectPlanListStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.projectListPage
import app.mcorg.presentation.templated.dsl.pages.projectListPageWithPlanView
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import io.ktor.server.application.ApplicationCall

data class ProjectListData(
    val world: World,
    val member: WorldMember,
    val projects: List<ProjectListItem>,
    val edges: List<ProjectResourceEdge>,
)

suspend fun ApplicationCall.handleGetProjectList() {
    val user = getUser()
    val worldId = getWorldId()
    val view = request.queryParameters["view"]?.takeIf { it == "plan" } ?: "execute"

    if (view == "plan") {
        handlePipeline(
            onSuccess = { (world, member, projects) ->
                val isAdmin = member.worldRole.isHigherThanOrEqualTo(Role.ADMIN)
                respondHtml(projectListPageWithPlanView(user, world, projects, isWorldAdmin = isAdmin))
            }
        ) {
            val world = GetWorldStep.run(worldId)
            val worldMember = GetWorldMemberStep(worldId).run(user.id)
            val projects = GetProjectPlanListStep(worldId).run(Unit)
            Triple(world, worldMember, projects)
        }
    } else {
        handlePipeline(
            onSuccess = { (world, member, projects, edges) ->
                val isAdmin = member.worldRole.isHigherThanOrEqualTo(Role.ADMIN)
                respondHtml(projectListPage(user, world, projects, view, isWorldAdmin = isAdmin, edges = edges))
            }
        ) {
            val world = GetWorldStep.run(worldId)
            val worldMember = GetWorldMemberStep(worldId).run(user.id)
            val projects = GetProjectListStep(worldId).run(Unit)
            val edges = GetProjectEdgesStep(worldId).run(Unit)
            ProjectListData(world, worldMember, projects, edges)
        }
    }
}
