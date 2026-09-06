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
 *   so its demand accumulates with any other demand for the same item. Keyed by tag id,
 *   but read by member set: an entry under any id naming that set answers the question
 *   for all of them ([TagIdentity], MCO-486).
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
 *   preference over loot sources (bulk crafting beats repeated gathering). **This is the only
 *   demand-sensitive thing in planning, and it dies with [SelectionScorer]** — MCO-522 decided
 *   the replacement cost model stays demand-independent rather than growing an amortised
 *   `setup/demand + per_unit` effort term. Measured on 1.21.4 / world 3 with
 *   `cost-diagnostics demands=10,100,1000`: 28 of 996 items change their committed source with
 *   demand, and priced against the end each one drops, **25 are a strict improvement, 3 are ties
 *   and none is a loss**. So the threshold was never modelling an effect of size — on 19 items it
 *   was swinging between two answers that a third source beats outright (16 wool colours are
 *   sheared, not killed or crafted; iron/gold/copper are blasted), on 7 it was correcting a bad
 *   small-demand default the cost model reaches at every size, and on `leather` it *introduced*
 *   an error (crafting from 4 rabbit hides at 0.5 hide/kill costs 4.05 min against 0.50 min for a
 *   cow). A fixed cost that genuinely does not divide per item — one trip to the mine buying
 *   cobblestone, coal and iron — belongs to the assembled plan rather than to unit cost; that is
 *   MCO-493's finding and its home is `PlanQuantifier`.
 * @param maxDepth recursion bound for chain expansion.
 * @param woodSpecies which tree the player is farming, e.g. `"birch"` (MCO-409). Settles every
 *   wood choice in the plan at once — `#planks`, `#wooden_slabs` and `#logs` are three askings
 *   of one question. Null means unanswered, and those tags stay
 *   [PlanNodeStatus.OPEN_TAG]; there is deliberately no default, because which wood you farm is
 *   a real preference rather than something to assume. Validate against
 *   [MemberPrior.isKnownSpecies] before storing one.
 * @param scorerMutation **diagnostics only.** Turns off one of the [SelectionScorer]
 *   behaviours no test pins, so a differential can measure what it actually decides on the
 *   real graph (MCO-490). [ScorerMutation.NONE] is the shipped behaviour and the default;
 *   nothing in production ever passes anything else. See [ScorerMutation] for why this is a
 *   measurement tool rather than a tuning surface.
 */
data class PlanContext(
    val recipeThreshold: Int = 100,
    val maxDepth: Int = 16,
    val woodSpecies: String? = null,
    val scorerMutation: ScorerMutation = ScorerMutation.NONE
)
