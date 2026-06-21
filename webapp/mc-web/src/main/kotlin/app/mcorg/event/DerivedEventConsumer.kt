package app.mcorg.event

/** Resource-progress milestones, in percent, that emit a [ResourceMilestoneReached]. */
internal val RESOURCE_MILESTONES = listOf(25, 50, 75, 100)

/**
 * The single highest milestone newly crossed when project progress moves from [previousDone] to
 * [newDone] out of [requiredTotal], or `null` if none is crossed. A milestone is crossed when the
 * previous percentage was strictly below it and the new percentage is at or above it.
 *
 * Only the highest crossed milestone fires: a jump that spans several (e.g. 0% → 80%, or 0% → 100%)
 * reports just the top one (75, or 100), never the intermediates.
 */
internal fun milestoneReached(previousDone: Int, newDone: Int, requiredTotal: Int): Int? {
    if (requiredTotal <= 0) return null
    val previousPercent = previousDone.toDouble() / requiredTotal * 100.0
    val newPercent = newDone.toDouble() / requiredTotal * 100.0
    return RESOURCE_MILESTONES.lastOrNull { previousPercent < it && newPercent >= it }
}

/**
 * Re-publishes derived events from a single domain event ([SeamEvent.data] of the source is enough —
 * the consumer never touches the database). Derivation is one level deep: [DerivedEvent] inputs are
 * ignored, so a re-published event can never trigger further derivation.
 *
 * Derivations:
 *  - [ResourceCountUpdated] → [ResourceMilestoneReached] (per milestone crossed) and, when the
 *    project's resources become fully satisfied, [ProjectResourcesComplete].
 *  - [DependencyEdgeRemoved] → [ProjectUnblocked] for each newly unblocked dependent.
 */
class DerivedEventConsumer(private val bus: EventBus) : EventHandler {
    override suspend fun handle(event: SeamEvent) {
        if (event is DerivedEvent) return // one level of derivation only

        when (event) {
            is ResourceCountUpdated -> deriveFromResourceCount(event)
            is DependencyEdgeRemoved -> deriveFromDependencyRemoval(event)
            else -> Unit
        }
    }

    private fun deriveFromResourceCount(event: ResourceCountUpdated) {
        milestoneReached(event.projectPreviousDone, event.projectNewDone, event.projectRequired)?.let { milestone ->
            bus.publish(
                ResourceMilestoneReached(
                    worldId = event.worldId,
                    actorId = event.actorId,
                    timestamp = event.timestamp,
                    projectId = event.projectId,
                    milestone = milestone,
                )
            )
        }

        val wasIncomplete = event.projectPreviousDone < event.projectRequired
        val nowComplete = event.projectRequired > 0 && event.projectNewDone >= event.projectRequired
        if (wasIncomplete && nowComplete) {
            bus.publish(
                ProjectResourcesComplete(
                    worldId = event.worldId,
                    actorId = event.actorId,
                    timestamp = event.timestamp,
                    projectId = event.projectId,
                )
            )
        }
    }

    private fun deriveFromDependencyRemoval(event: DependencyEdgeRemoved) {
        event.unblockedDependentProjectIds.forEach { dependentId ->
            bus.publish(
                ProjectUnblocked(
                    worldId = event.worldId,
                    actorId = event.actorId,
                    timestamp = event.timestamp,
                    projectId = dependentId,
                    unblockedByProjectId = event.projectId,
                )
            )
        }
    }
}
