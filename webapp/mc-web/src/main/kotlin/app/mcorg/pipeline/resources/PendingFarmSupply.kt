package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep

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

/**
 * The farms this project is waiting on, as prerequisites rather than as a courtesy notice
 * (MCO-461).
 *
 * MCO-299 shipped this as a promise — "63,213 Redstone Dust will come from Witch Hut Farm once
 * it is running". True, and useful, but it is not an *ordering fact*: nothing on the page said
 * the witch farm comes first, which is why MCO-294 could offer to import that same farm again
 * right beside it. #417 stopped the contradiction; this is the other half — the page saying
 * what the relationship actually is.
 *
 * ## Why this reads the roadmap's edges rather than the planned-farm list
 *
 * [pendingFarmSuppliesFor] answers "which planned farm will make this", which is the right
 * question for suppressing a suggestion (#417's `alreadyCovered`) and the wrong one for
 * claiming a prerequisite: it applies no threshold and knows nothing about loops. A cobblestone
 * farm needing 20 gunpowder is not waiting on the witch farm in any sense worth sequencing, and
 * where two farms supply each other the answer depends on an ordering the user chose.
 *
 * [GetFarmSupplyEdgesStep] already encodes both rules — the farm-scale threshold (MCO-460) and
 * `roadmap_cycle_order` — because the roadmap needs them. Reading the same step here is what
 * makes the project page and the roadmap incapable of disagreeing about what blocks what, which
 * is the whole point of MCO-461. Two independent derivations of "is this a prerequisite" is the
 * bug, not the fix.
 *
 * Soft-fails to none, like every other decoration on the plan: a missing prerequisite line is a
 * worse page, a failed query is no page.
 */
suspend fun prerequisiteFarmsFor(worldId: Int, projectId: Int): List<PendingFarmSupply> {
    val edges = GetFarmSupplyEdgesStep(worldId).process(Unit).getOrNull() ?: return emptyList()

    return edges
        // `isBlocking` is producerState != DONE (MCO-287): a farm already running supplies now
        // and is nobody's prerequisite. That is the same rule the roadmap draws with.
        .filter { it.consumerId == projectId && it.isBlocking }
        .groupBy { it.producerId to it.producerName }
        .mapNotNull { (producer, group) ->
            val items = group
                .mapNotNull { edge ->
                    val name = edge.itemName ?: return@mapNotNull null
                    val quantity = edge.quantity ?: return@mapNotNull null
                    PendingFarmItem(itemId = name, itemName = name, quantity = quantity)
                }
                .distinctBy { it.itemId }
                .sortedWith(compareByDescending<PendingFarmItem> { it.quantity }.thenBy { it.itemName })
            // A declared `project_dependencies` row carries no item and no quantity, so it
            // produces no line here — the roadmap is where non-resource sequencing shows up.
            if (items.isEmpty()) null
            else PendingFarmSupply(projectId = producer.first, projectName = producer.second, items = items)
        }
        .sortedBy { it.projectName }
}
