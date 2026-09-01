package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.engine.plan.TargetTree
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceStep
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.pipeline.world.settings.general.versionGapsForPlan
import app.mcorg.pipeline.resources.GatheringPlanInput
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.resources.GetFarmScaleThresholdStep
import app.mcorg.pipeline.resources.farmSuggestionsFor
import app.mcorg.pipeline.resources.GenerateGatheringPlanStep
import app.mcorg.pipeline.resources.pendingFarmSuppliesFor
import app.mcorg.pipeline.resources.prerequisiteFarmsFor
import app.mcorg.pipeline.resources.GetPlanOverridesStep
import app.mcorg.pipeline.resources.buildCandidateCounts
import app.mcorg.pipeline.resources.buildNodeIngredients
import app.mcorg.pipeline.resources.drillTreeFor
import app.mcorg.pipeline.resources.getGraphForWorld
import app.mcorg.pipeline.project.resources.GetResourceProductionStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.projectDetailPage
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

suspend fun ApplicationCall.handleGetProject() {
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    val project = when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> result.value
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
    }

    // Resolve lens. An explicit ?lens= query param wins (so reload/share of a pushed lens
    // URL renders the right lens); otherwise fall back to the saved view preference.
    // Old "plan"/"execute" values map to "list".
    fun normalizeLens(value: String?): String? = when (value) {
        "plan", "execute", "list" -> "list"
        "next", "sessions" -> value
        else -> null
    }
    val lens = normalizeLens(request.queryParameters["lens"])
        ?: normalizeLens(GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId)).getOrNull())
        ?: "list"

    val resources = GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()

    val productions = GetResourceProductionStep.process(projectId).getOrNull() ?: emptyList()

    val tasks = when (val result = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL"))) {
        is Result.Success -> result.value
        is Result.Failure -> {
            respondBadRequest("Failed to load tasks")
            return
        }
    }

    // Derive the gathering plan — failure is non-fatal (renders fallback state)
    val plan = when (val result = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> result.value
        is Result.Failure -> when (result.error) {
            is AppFailure.ValidationError -> null
            is AppFailure.DatabaseError.NotFound -> null
            else -> {
                defaultHandleError(result.error)
                return
            }
        }
    }

    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    val worldName = getWorldName(worldId)

    // Load persisted progress for all items in the project (covers derived activities too)
    val progressMap = GetProgressForProjectStep.process(projectId).getOrNull() ?: emptyMap()

    // Two different questions, deliberately two sources (MCO-461):
    //  - what the world already covers, for suppressing a suggestion (#417) — no threshold,
    //    because a farm that covers an item makes a second design for it pointless at any size
    //  - what this project *waits on*, for the prerequisite line — thresholded and
    //    cycle-ordered, because that is an ordering claim and shares the roadmap's rules
    val coveredByPlannedFarms = pendingFarmSuppliesFor(worldId, projectId, plan)
    val prerequisiteFarms = prerequisiteFarmsFor(worldId, projectId)

    // The world's "worth a farm" line (MCO-401). Falls back to the default rather than
    // failing the page: a missing marker beats a missing plan.
    val farmScaleThreshold = GetFarmScaleThresholdStep.process(worldId).getOrNull()
        ?: World.DEFAULT_FARM_SCALE_THRESHOLD

    // Designs in the bank that answer this plan's demand (MCO-294). Scoped to the viewer,
    // because the bank is public ideas plus their own — a shared computation would show one
    // user another's private designs.
    val farmSuggestions = farmSuggestionsFor(
        plan, farmScaleThreshold, user.id, projectId, coveredByPlannedFarms, project.importedFromIdea?.first,
    )

    // ?drill=<item> deep-links into a target's chain so reload/share lands on the drill,
    // not the plan. Resolves only when the plan derives and the item is an actual target;
    // otherwise falls through to the normal lens render.
    val drillItemId = request.queryParameters["drill"]
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
        ?.takeIf { it.isNotBlank() }
    val drillTarget: TargetTree? = drillItemId?.let { plan?.drillTreeFor(it) }
    val drillGraph = if (drillTarget != null) getGraphForWorld(worldId) else null
    val drillCandidateCounts = if (drillTarget != null) buildCandidateCounts(drillTarget, drillGraph) else emptyMap()
    val drillNodeIngredients = if (drillTarget != null && plan != null) buildNodeIngredients(plan) else emptyMap()
    val drillOverrides = if (drillTarget != null) GetPlanOverridesStep.process(projectId).getOrNull() ?: PlanOverrides.NONE else PlanOverrides.NONE

    val versionGaps = versionGapsForPlan(projectId, plan)

    respondHtml(
        projectDetailPage(
            user, project, worldName, resources, tasks, lens,
            isWorldAdmin = isAdmin, plan = plan, progressMap = progressMap,
            productions = productions,
            pendingFarms = prerequisiteFarms,
            drillTarget = drillTarget, drillCandidateCounts = drillCandidateCounts,
            drillNodeIngredients = drillNodeIngredients, drillHighlightItemId = drillItemId,
            drillOverrides = drillOverrides, drillGraph = drillGraph,
            farmScaleThreshold = farmScaleThreshold,
            farmSuggestions = farmSuggestions,
            versionGaps = versionGaps,
        )
    )
}
