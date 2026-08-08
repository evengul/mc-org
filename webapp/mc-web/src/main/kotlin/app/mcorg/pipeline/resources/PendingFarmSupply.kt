package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus

/** One item a not-yet-operational farm has promised, with the amount this plan still needs. */
data class PendingFarmItem(
    val itemId: String,
    val itemName: String,
    val quantity: Long,
)

/**
 * The partial-dependency notice's data (MCO-299): a farm project that is not operational
 * yet, and the items in *this* plan it will supply once it is.
 */
data class PendingFarmSupply(
    val projectId: Int,
    val projectName: String,
    val items: List<PendingFarmItem>,
)

/**
 * Loads the world's not-yet-operational farms and matches them against [plan].
 *
 * Soft-fails to an empty list: the notice is a courtesy on top of the plan, and a failed
 * lookup must not take the plan down with it. Called by every render path that shows the
 * grouped plan, so the notice survives HTMX re-renders.
 */
suspend fun pendingFarmSuppliesFor(
    worldId: Int,
    projectId: Int,
    plan: GatheringPlan?,
): List<PendingFarmSupply> {
    if (plan == null) return emptyList()
    val plannedFarms = GetWorldPlannedFarmsStep
        .process(WorldPlannedFarmsInput(worldId = worldId, excludeProjectId = projectId))
        .getOrNull()
        ?: return emptyList()
    return buildPendingFarmSupplies(plan, plannedFarms)
}

/**
 * Matches a plan's still-manual work against the world's not-yet-operational farms
 * ([GetWorldPlannedFarmsStep]).
 *
 * Only activities the planner is still asking you to do by hand count: anything already
 * [PlanNodeStatus.SUPPLIED] is solved (by an operational farm or a linked project) and has
 * nothing pending about it. Everything else the farm produces is work you must do now but
 * will not have to repeat — which is exactly what the notice says.
 *
 * Items are ordered biggest-first (the amount you would otherwise gather by hand is the
 * reason the notice matters), farms by name for a stable read.
 */
fun buildPendingFarmSupplies(
    plan: GatheringPlan,
    plannedFarms: List<PlannedFarmRow>,
): List<PendingFarmSupply> {
    if (plannedFarms.isEmpty()) return emptyList()

    val manualByItemId = plan.activityList
        .filter { it.status != PlanNodeStatus.SUPPLIED }
        .associateBy { it.item.id }
    if (manualByItemId.isEmpty()) return emptyList()

    return plannedFarms
        .groupBy { it.projectId to it.projectName }
        .mapNotNull { (project, rows) ->
            val items = rows
                .distinctBy { it.itemId }
                .mapNotNull { row ->
                    val activity = manualByItemId[row.itemId] ?: return@mapNotNull null
                    PendingFarmItem(
                        itemId = row.itemId,
                        itemName = activity.item.name,
                        quantity = activity.quantity,
                    )
                }
                .sortedWith(compareByDescending<PendingFarmItem> { it.quantity }.thenBy { it.itemName })
            if (items.isEmpty()) null
            else PendingFarmSupply(projectId = project.first, projectName = project.second, items = items)
        }
        .sortedBy { it.projectName }
}
