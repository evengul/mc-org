package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId

/**
 * A single planning target: "I need [amount] of [item]".
 *
 * Amounts are net of anything already collected — the caller (mc-web) subtracts
 * `collected` from `required` before calling the engine. The engine never sees
 * project progress.
 */
data class PlanTarget(
    val item: MinecraftId,
    val amount: Long
)

/**
 * An external supply that satisfies an item without expanding its production chain.
 *
 * Wherever a supplied item appears in a chain — as a target or as an intermediate —
 * selection terminates there with a [PlanNodeStatus.SUPPLIED] leaf carrying this label.
 * The caller folds world productions (farms) and linked projects into one map.
 */
sealed interface SupplySource {
    val label: String

    /** The item is produced by an existing farm / world production. */
    data class Farm(override val label: String) : SupplySource

    /** The item is produced by another project the resource is linked to. */
    data class LinkedProject(val projectId: Int, override val label: String) : SupplySource
}

/**
 * User-pinned choices that constrain selection.
 *
 * @param sourceByItem itemId -> sourceKey ([app.mcorg.engine.model.SourceNode.getKey]).
 *   The pinned source is used for that item regardless of score. A pin that is
 *   infeasible (cyclic or unknown) leaves the node [PlanNodeStatus.BLOCKED] rather
 *   than silently falling back — the user asked for something impossible and
 *   should see that.
 * @param tagMember tagId -> member itemId. Disambiguates an [PlanNodeStatus.OPEN_TAG]
 *   node ("any planks" -> oak_planks). The member item replaces the tag in the DAG,
 *   so its demand accumulates with any other demand for the same item.
 */
data class PlanOverrides(
    val sourceByItem: Map<String, String> = emptyMap(),
    val tagMember: Map<String, String> = emptyMap()
) {
    companion object {
        val NONE = PlanOverrides()
    }
}

/**
 * Tunables for a planning run. The graph itself is passed separately.
 *
 * @param recipeThreshold at or above this demand, recipe sources get a strong
 *   preference over loot sources (bulk crafting beats repeated gathering).
 * @param maxDepth recursion bound for chain expansion.
 */
data class PlanContext(
    val recipeThreshold: Int = 100,
    val maxDepth: Int = 16
)
