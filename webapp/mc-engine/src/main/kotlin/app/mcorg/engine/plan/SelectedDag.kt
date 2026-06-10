package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.SourceNode

/**
 * Output of the select() stage: one source decision per item, shaped as a DAG.
 *
 * Every item appearing anywhere in any target's chain resolves to exactly one
 * node here — shared ingredients are a single node, not one copy per target.
 * The DAG is acyclic by construction: a candidate source whose ingredient chain
 * would contain the item itself is structurally rejected during selection.
 *
 * This is the cheap-to-recompute half of a plan. quantify() turns it into a
 * [GatheringPlan] by propagating target amounts through it.
 *
 * @param nodes itemId -> selection, in discovery order (targets first, then
 *   ingredients depth-first).
 * @param roots requested target itemId -> the node that fulfils it. Differs from
 *   the identity mapping only when a tag target was redirected to a member item
 *   via [PlanOverrides.tagMember].
 */
class SelectedDag(
    val nodes: Map<String, SelectedNode>,
    val roots: Map<String, String>
)

/**
 * The selection decision for a single item.
 *
 * Exactly one of the following shapes, mirrored by [status]:
 * - [PlanNodeStatus.RESOLVED]: [source] chosen, [requires] non-empty.
 * - [PlanNodeStatus.RAW_GATHER]: [source] chosen, no inputs — a deliberate
 *   world-gathering terminal (mine, kill, loot).
 * - [PlanNodeStatus.SUPPLIED]: terminated by the supplied map; [supply] is set.
 * - [PlanNodeStatus.OPEN_TAG]: a tag awaiting a variant choice; nothing chosen.
 * - [PlanNodeStatus.BLOCKED]: no feasible source (dead end, cycle, or an
 *   impossible pin); nothing chosen.
 *
 * @param producedQuantity how many of the item one execution of [source] yields
 *   (1 for terminals without a source).
 * @param requires per-execution ingredient amounts, pointing at sibling nodes.
 */
data class SelectedNode(
    val item: MinecraftId,
    val status: PlanNodeStatus,
    val source: SourceNode? = null,
    val supply: SupplySource? = null,
    val producedQuantity: Int = 1,
    val requires: List<PlanRequirement> = emptyList()
)
