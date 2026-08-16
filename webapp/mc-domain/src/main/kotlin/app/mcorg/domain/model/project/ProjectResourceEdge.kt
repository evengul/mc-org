package app.mcorg.domain.model.project

/**
 * A producer→consumer edge between two projects in the same world: the consumer
 * has a resource requirement solved by the producer (itemName set), or an
 * explicit project dependency (itemName null). The consumer is blocked by the
 * edge until the producer reaches a terminal DONE state.
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
) {
    val isBlocking: Boolean
        get() = producerState != ProjectState.DONE

    /** Edges toward terminal consumers are off the board — nobody is waiting. */
    val isLive: Boolean
        get() = !consumerState.isTerminal
}
