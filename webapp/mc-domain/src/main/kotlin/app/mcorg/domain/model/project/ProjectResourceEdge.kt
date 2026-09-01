package app.mcorg.domain.model.project

/**
 * A producer→consumer edge between two projects in the same world: the consumer
 * has a resource requirement solved by the producer (itemName set), or an
 * explicit project dependency (itemName null). The consumer is blocked by the
 * edge until the producer reaches a terminal DONE state — unless something else
 * in the world already supplies the item ([supersededBySupplier], MCO-466).
 */
data class ProjectResourceEdge(
    val consumerId: Int,
    val consumerName: String,
    val consumerState: ProjectState,
    val producerId: Int,
    val producerName: String,
    val itemName: String?,
    val producerState: ProjectState,
    /**
     * How much of [itemName] the consumer's derived plan needs (MCO-316).
     *
     * Null where the edge does not come from derived demand — a manual `project_dependencies`
     * row has no item, and a `solved_by_project_id` link names a declared row rather than a
     * planned quantity. Null means "no number to show", never "zero".
     */
    val quantity: Long? = null,
    /**
     * Another project in this world already produces [itemName] and is operational (MCO-466).
     *
     * A world can have two producers of one item — a witch farm that has been running for
     * months and a ghast farm still being built both make gunpowder. Judged on its own the
     * unfinished one looks like a prerequisite, so the roadmap said a build was blocked for
     * 5 gunpowder that the finished farm was already supplying on the same page.
     *
     * The edge is still true and still drawn; what it is not is *blocking*. Set only on
     * derived farm-supply edges: a `solved_by_project_id` link is a person naming the producer
     * they want, and no amount of other supply overrides that choice.
     */
    val supersededBySupplier: Boolean = false,
) {
    /**
     * Whether the consumer is still waiting on this producer.
     *
     * Two conditions, and the second is not redundant: the producer must be unfinished, *and*
     * nothing already-running must cover the same item (MCO-466). Producer state alone was the
     * rule until a world grew a second producer for one item.
     */
    val isBlocking: Boolean
        get() = producerState != ProjectState.DONE && !supersededBySupplier

    /** Edges toward terminal consumers are off the board — nobody is waiting. */
    val isLive: Boolean
        get() = !consumerState.isTerminal
}
