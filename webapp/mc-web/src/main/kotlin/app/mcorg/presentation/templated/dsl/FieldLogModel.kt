package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState

/**
 * View model for the Field Log: groups projects by state and folds the
 * dependency edges into per-project feeds/blocked-by captions. Pure logic,
 * unit-testable without templates or database.
 */
data class FieldLogModel(
    val active: List<ProjectListItem>,
    val pending: List<ProjectListItem>,
    val paused: List<ProjectListItem>,
    val done: List<ProjectListItem>,
    val cancelled: List<ProjectListItem>,
    val archived: List<ProjectListItem>,
    val feedsByProject: Map<Int, List<ProjectResourceEdge>>,
    val blockedByProject: Map<Int, List<ProjectResourceEdge>>,
) {
    fun isBlocked(projectId: Int): Boolean = blockedByProject.containsKey(projectId)

    fun feeds(projectId: Int): List<ProjectResourceEdge> = feedsByProject[projectId] ?: emptyList()

    fun blockedBy(projectId: Int): List<ProjectResourceEdge> = blockedByProject[projectId] ?: emptyList()

    companion object {
        fun of(projects: List<ProjectListItem>, edges: List<ProjectResourceEdge>): FieldLogModel {
            val projectIds = projects.map { it.id }.toSet()
            val relevant = edges.filter { it.consumerId in projectIds || it.producerId in projectIds }

            // A project's outgoing edges only matter while the consumer still wants them
            // (terminal consumers are off the board).
            val feeds = relevant
                .filter { it.isLive }
                .distinctBy { Triple(it.producerId, it.consumerId, it.itemName) }
                .groupBy { it.producerId }

            val blocked = relevant
                .filter { it.isBlocking && it.isLive }
                .distinctBy { Triple(it.producerId, it.consumerId, it.itemName) }
                .groupBy { it.consumerId }

            val byState = projects.groupBy { it.state }

            val active = (byState[ProjectState.ACTIVE] ?: emptyList())
                .sortedWith(
                    compareBy<ProjectListItem> { blocked.containsKey(it.id) }
                        .thenByDescending { it.progressFraction() }
                        .thenBy { it.name }
                )

            return FieldLogModel(
                active = active,
                pending = (byState[ProjectState.PENDING] ?: emptyList()).sortedBy { it.name },
                paused = (byState[ProjectState.PAUSED] ?: emptyList()).sortedBy { it.name },
                done = (byState[ProjectState.DONE] ?: emptyList()).sortedBy { it.name },
                cancelled = (byState[ProjectState.CANCELLED] ?: emptyList()).sortedBy { it.name },
                archived = (byState[ProjectState.ARCHIVED] ?: emptyList()).sortedBy { it.name },
                feedsByProject = feeds,
                blockedByProject = blocked,
            )
        }

        private fun ProjectListItem.progressFraction(): Double =
            if (resourcesRequired > 0) resourcesGathered.toDouble() / resourcesRequired else 0.0
    }
}
