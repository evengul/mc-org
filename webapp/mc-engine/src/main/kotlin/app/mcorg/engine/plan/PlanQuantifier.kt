package app.mcorg.engine.plan

import app.mcorg.engine.model.ItemSourceGraph

/**
 * Hook for surplus reuse. quantify() asks the policy, per node, how much of the
 * node's demand is already covered externally — e.g. by leftovers banked at
 * other nodes or multi-output byproducts — before computing craft counts.
 *
 * V1 never draws ([NONE]); the V2 surplus optimizer plugs in here without
 * changing the propagation algorithm.
 */
fun interface SurplusPolicy {

    /** Amount of [demand] for [itemId] covered externally. Must be in `0..demand`. */
    fun draw(itemId: String, demand: Long): Long

    companion object {
        val NONE = SurplusPolicy { _, _ -> 0L }
    }
}

/**
 * Stage 2 of the planning engine: decide *how much* of everything is needed.
 *
 * Propagates target amounts through the [SelectedDag] in topological order
 * (every consumer before its ingredients), so each shared node accumulates its
 * total demand across all targets **before** craft counts are computed —
 * accumulate-then-ceil. Rounding up happens once per node:
 *
 *     crafts   = ceil(totalDemand / producedQuantity)
 *     leftover = crafts * producedQuantity - totalDemand
 *
 * The leftover is reported per node (the leftover bank) but never reused in V1.
 * OPEN_TAG and BLOCKED nodes get quantities too — demand is known even when the
 * source is not.
 */
object PlanQuantifier {

    fun quantify(
        dag: SelectedDag,
        targets: List<PlanTarget>,
        surplus: SurplusPolicy = SurplusPolicy.NONE
    ): GatheringPlan {
        val demands = HashMap<String, Long>(dag.nodes.size)
        for (target in targets) {
            if (target.amount <= 0) continue
            val rootId = dag.roots[target.item.id] ?: target.item.id
            if (rootId in dag.nodes) demands.merge(rootId, target.amount, Long::plus)
        }

        val nodes = LinkedHashMap<String, PlanNode>(dag.nodes.size)
        for (id in consumersFirst(dag)) {
            val selected = dag.nodes.getValue(id)
            val demand = demands[id] ?: 0L
            val drawn = surplus.draw(id, demand).coerceIn(0L, demand)
            val net = demand - drawn
            val crafts = ceilDiv(net, selected.producedQuantity)
            val leftover = crafts * selected.producedQuantity - net

            for (requirement in selected.requires) {
                demands.merge(requirement.itemId, crafts * requirement.quantityPerCraft, Long::plus)
            }

            nodes[id] = PlanNode(
                item = selected.item,
                quantity = demand,
                crafts = crafts,
                leftover = leftover,
                status = selected.status,
                source = selected.source,
                supply = selected.supply,
                producedQuantity = selected.producedQuantity,
                requires = selected.requires
            )
        }

        return GatheringPlan(
            nodes = nodes,
            targets = targets.filter { it.amount > 0 },
            roots = dag.roots
        )
    }

    /**
     * Topological order over the selection: a node appears only after everything
     * consuming it. Deterministic: ties resolve by item id. The selector
     * guarantees acyclicity; if a hand-built dag cycles anyway, the remaining
     * nodes are appended by id so quantification still terminates.
     */
    private fun consumersFirst(dag: SelectedDag): List<String> {
        val pendingConsumers = HashMap<String, Int>(dag.nodes.size)
        for (id in dag.nodes.keys) pendingConsumers[id] = 0
        for (node in dag.nodes.values) {
            for (requirement in node.requires) {
                if (requirement.itemId in pendingConsumers) {
                    pendingConsumers.merge(requirement.itemId, 1, Int::plus)
                }
            }
        }

        val ready = sortedSetOf<String>()
        pendingConsumers.forEach { (id, count) -> if (count == 0) ready.add(id) }

        val order = ArrayList<String>(dag.nodes.size)
        while (ready.isNotEmpty()) {
            val id = ready.first().also { ready.remove(it) }
            order.add(id)
            for (requirement in dag.nodes.getValue(id).requires) {
                val remaining = pendingConsumers[requirement.itemId]?.minus(1) ?: continue
                pendingConsumers[requirement.itemId] = remaining
                if (remaining == 0) ready.add(requirement.itemId)
            }
        }

        if (order.size < dag.nodes.size) {
            dag.nodes.keys.filter { it !in order.toSet() }.sorted().forEach { order.add(it) }
        }
        return order
    }
}

/**
 * The two engine stages composed: select then quantify. Callers that don't need
 * to cache or inspect the intermediate [SelectedDag] use this.
 */
object GatheringPlanner {

    fun plan(
        graph: ItemSourceGraph,
        targets: List<PlanTarget>,
        supplied: Map<String, SupplySource> = emptyMap(),
        overrides: PlanOverrides = PlanOverrides.NONE,
        context: PlanContext = PlanContext()
    ): GatheringPlan {
        val dag = PlanSelector.select(graph, targets, supplied, overrides, context)
        return PlanQuantifier.quantify(dag, targets)
    }
}
