package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.resources.GatheringPlanInput
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.resources.GetFarmScaleThresholdStep
import app.mcorg.pipeline.resources.farmSuggestionsFor
import app.mcorg.pipeline.resources.GenerateGatheringPlanStep
import app.mcorg.pipeline.resources.pendingFarmSuppliesFor
import app.mcorg.pipeline.resources.prerequisiteFarmsFor
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.gatheringPlannerFragment
import app.mcorg.pipeline.world.settings.general.versionGapsForPlan
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetDetailContent() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    // The retired PLAN/EXECUTE toggle's old ?view= links resolve to the default List lens.
    val lens = request.queryParameters["lens"]
        ?.takeIf { it == "list" || it == "next" || it == "sessions" }
        ?: "list"

    val project = when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> result.value
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
    }

    val resources = GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()
    val tasks = when (val result = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL"))) {
        is Result.Success -> result.value
        is Result.Failure -> {
            respondBadRequest("Failed to load tasks")
            return
        }
    }

    // Derive the gathering plan — failure is non-fatal. A null plan renders the
    // definition/empty fallback state instead of grouped activity sections.
    val plan = when (val result = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> result.value
        is Result.Failure -> when (result.error) {
            // No positive targets (nothing defined yet, or all collected).
            is AppFailure.ValidationError -> null
            // World's Minecraft version has no ingested graph yet.
            is AppFailure.DatabaseError.NotFound -> null
            // Unexpected error — surface it
            else -> {
                defaultHandleError(result.error)
                return
            }
        }
    }

    // Load persisted progress for all items in the project (covers derived activities too)
    val progressMap = GetProgressForProjectStep.process(projectId).getOrNull() ?: emptyMap()

    // Two different questions, deliberately two sources (MCO-461):
    //  - what the world already covers, for suppressing a suggestion (#417) — no threshold,
    //    because a farm that covers an item makes a second design for it pointless at any size
    //  - what this project *waits on*, for the prerequisite line — thresholded and
    //    cycle-ordered, because that is an ordering claim and shares the roadmap's rules
    val coveredByPlannedFarms = pendingFarmSuppliesFor(worldId, projectId, plan)
    val prerequisiteFarms = prerequisiteFarmsFor(worldId, projectId)

    // Same fallback as the full page (MCO-401): the lens fragment must not silently drop the
    // farm-scale roll-up, or switching lens would look like the markers disappeared.
    val farmScaleThreshold = GetFarmScaleThresholdStep.process(worldId).getOrNull()
        ?: World.DEFAULT_FARM_SCALE_THRESHOLD

    // Same reason the threshold is here (MCO-401): switching lens must not look like the
    // suggestions disappeared.
    val farmSuggestions = farmSuggestionsFor(
        plan, farmScaleThreshold, getUser().id, projectId, coveredByPlannedFarms, project.importedFromIdea?.first,
    )

    // The roll-up's threshold is a link to world settings for admins only, so the fragment
    // needs the same role answer the full page computes.
    val isAdmin = ValidateWorldMemberRole<Unit>(getUser(), Role.ADMIN, worldId).process(Unit) is Result.Success

    respondHtml(
        gatheringPlannerFragment(
            project, resources, tasks, plan, lens, progressMap, prerequisiteFarms, farmScaleThreshold,
            farmSuggestions, versionGapsForPlan(projectId, plan), isAdmin,
        )
    )
}
