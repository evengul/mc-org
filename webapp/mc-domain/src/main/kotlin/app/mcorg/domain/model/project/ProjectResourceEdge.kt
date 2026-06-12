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
    val producerId: Int,
    val producerName: String,
    val itemName: String?,
    val producerState: ProjectState,
) {
    val isBlocking: Boolean
        get() = producerState != ProjectState.DONE
}
