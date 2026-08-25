package app.mcorg.domain.model.world

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType

/**
 * Represents a complete roadmap visualization of all projects and their dependencies within a world.
 * This model uses a recursive structure to represent the dependency graph, allowing for efficient
 * traversal and visualization of project relationships.
 *
 * Business Rules:
 * - All dependencies must be within the same world
 * - Projects can have multiple dependencies and dependents
 *
 * **Cycles exist and are not an error (MCO-460).** This said "no circular dependencies are
 * allowed (enforced at creation time)" until 2026-08-25, and nothing enforced it — because
 * nothing could. Edges are derived from real demand, so two farms that each consume a
 * farm-scale amount of the other's output close a genuine loop out of two true facts. See
 * [cycles] for what the roadmap does about it.
 */
data class Roadmap(
    val worldId: Int,
    val worldName: String,
    val nodes: List<RoadmapNode>,
    val edges: List<RoadmapEdge>,
    val layers: List<RoadmapLayer>,
    /**
     * Loops in the derived graph that no rule broke, each with the edge currently set aside
     * so that [layers] stays consistent, and the alternatives a person can pick instead.
     *
     * Empty is the normal case: MCO-401's farm-scale threshold drops footnote-sized edges
     * before they get here, which breaks most real loops. What lands here is two or more
     * farms genuinely waiting on each other.
     */
    val cycles: List<RoadmapCycle> = emptyList(),
    /** Pairs a person has already ordered by hand, so the choice can be seen and changed. */
    val resolvedOrders: List<RoadmapCycleOrder> = emptyList(),
) {
    /**
     * Gets all root nodes (projects with no dependencies)
     * These are the starting points for the roadmap visualization
     */
    fun getRootNodes(): List<RoadmapNode> = nodes.filter { node ->
        edges.none { edge -> edge.fromNodeId == node.projectId }
    }

    /**
     * Gets all leaf nodes (projects with no dependents)
     * These are the end points in the dependency chain
     */
    fun getLeafNodes(): List<RoadmapNode> = nodes.filter { node ->
        edges.none { edge -> edge.toNodeId == node.projectId }
    }

    /**
     * Gets the dependency depth (longest path from any root to any leaf)
     * Useful for determining visualization height
     */
    fun getMaxDepth(): Int = layers.size

    /**
     * Checks if the roadmap has any projects
     */
    fun isEmpty(): Boolean = nodes.isEmpty()

    /**
     * Gets statistics about the roadmap
     */
    fun getStatistics(): RoadmapStatistics = RoadmapStatistics(
        totalProjects = nodes.size,
        completedProjects = nodes.count { it.stage == ProjectStage.COMPLETED },
        blockedProjects = nodes.count { it.isBlocked },
        rootProjects = getRootNodes().size,
        leafProjects = getLeafNodes().size,
        maxDepth = getMaxDepth(),
        totalDependencies = edges.size
    )
}

/**
 * Represents a single project node in the roadmap graph.
 * Contains all information needed for visualization and interaction.
 */
data class RoadmapNode(
    val projectId: Int,
    val projectName: String,
    val projectType: ProjectType,
    val stage: ProjectStage,
    /**
     * Lifecycle state — the axis the rest of the app blocks and groups on. [stage] tracks
     * progress *within* a project; DONE vs not-DONE is what decides whether this node
     * still blocks the ones downstream of it.
     */
    val state: ProjectState,
    val tasksTotal: Int,
    val tasksCompleted: Int,
    val isBlocked: Boolean,
    val blockingProjectIds: List<Int>,
    val dependentProjectIds: List<Int>,
    val layer: Int
) {
    /**
     * Calculates the completion percentage of this project
     */
    fun getCompletionPercentage(): Int = if (tasksTotal > 0) {
        (tasksCompleted * 100) / tasksTotal
    } else {
        0
    }

    /**
     * Checks if this project is ready to start (all dependencies completed)
     */
    fun isReadyToStart(): Boolean = !isBlocked && stage == ProjectStage.IDEA

    /**
     * Checks if this project is in progress
     */
    fun isInProgress(): Boolean = stage in listOf(
        ProjectStage.DESIGN,
        ProjectStage.PLANNING,
        ProjectStage.RESOURCE_GATHERING,
        ProjectStage.BUILDING,
        ProjectStage.TESTING
    )

    /**
     * Checks if this project is completed
     */
    fun isCompleted(): Boolean = stage == ProjectStage.COMPLETED

    /**
     * Gets the count of blocking dependencies
     */
    fun getBlockingCount(): Int = blockingProjectIds.size
}

/**
 * Represents a dependency edge between two projects in the roadmap.
 * Direction: fromNode depends on toNode (toNode must be completed before fromNode can start)
 */
data class RoadmapEdge(
    val fromNodeId: Int,
    val fromNodeName: String,
    val toNodeId: Int,
    val toNodeName: String,
    val isBlocking: Boolean,
    /**
     * The resource this edge is about, when it is a resource edge. Null for a manual
     * project→project sequencing edge. The roadmap's "Depends on" cell names the project
     * *and* the resource, so the reader knows what to go and get.
     */
    val itemName: String? = null,
    /**
     * How much of [itemName] the consumer's derived plan needs (MCO-316).
     *
     * "Cobblestone Generator — 74,564 Cobblestone" is useful; the same cell without a number,
     * next to a single decorative block, was actively misleading. Null wherever there is no
     * number to show — a manual sequencing edge, or a declared link that names a row rather
     * than a planned quantity — and never a stand-in for zero.
     */
    val quantity: Long? = null,
) {
    /**
     * Checks if this edge represents a blocking dependency
     * (the dependency is not yet completed)
     */
    fun isCurrentlyBlocking(): Boolean = isBlocking
}

/**
 * Represents a horizontal layer in the roadmap visualization.
 * Projects in the same layer have the same dependency depth and can be worked on in parallel.
 */
data class RoadmapLayer(
    val depth: Int,
    val projectIds: List<Int>,
    val projectCount: Int
) {
    /**
     * Checks if this is the root layer (depth 0)
     */
    fun isRootLayer(): Boolean = depth == 0
}

/**
 * Statistics about the roadmap for quick insights
 */
data class RoadmapStatistics(
    val totalProjects: Int,
    val completedProjects: Int,
    val blockedProjects: Int,
    val rootProjects: Int,
    val leafProjects: Int,
    val maxDepth: Int,
    val totalDependencies: Int
) {
    /**
     * Calculates the overall completion percentage of the world
     */
    fun getOverallCompletionPercentage(): Int = if (totalProjects > 0) {
        (completedProjects * 100) / totalProjects
    } else {
        0
    }

    /**
     * Gets the count of projects in progress (not completed, not blocked)
     */
    fun getInProgressCount(): Int = totalProjects - completedProjects - blockedProjects

    /**
     * Checks if the roadmap has any blocking dependencies
     */
    fun hasBlockedProjects(): Boolean = blockedProjects > 0
}


/**
 * A loop in the derived dependency graph (MCO-460).
 *
 * Detected as a strongly connected component, not inferred from what the layering pass failed
 * to place: "unplaced" also catches everything downstream of a loop, which would name innocent
 * projects as part of a cycle they merely depend on.
 *
 * [breaking] is applied so [Roadmap.layers] is consistent the first time the page renders —
 * a roadmap that showed two projects each claiming to block the other would be the bug this
 * fixes. It is a guess, and the page says so; [options] is what the user picks from instead.
 */
data class RoadmapCycle(
    val projectIds: List<Int>,
    val projectNames: List<String>,
    /** Every edge in the loop, each phrased as "set this aside and X comes first". */
    val options: List<RoadmapCycleOption>,
    /** The option in force until someone chooses — the smallest claim in the loop. */
    val breaking: RoadmapCycleOption,
    /**
     * Whether this loop is worth interrupting someone for.
     *
     * False when [breaking] carries less than the world's farm-scale threshold: below that line
     * the smallest claim is a footnote, giving way is the obvious answer rather than a judgement
     * call, and the loop is broken silently. Such a cycle is still *broken* — it just never
     * reaches the page. Only balanced loops, where both claims are farm-scale, are questions.
     */
    val needsAnAnswer: Boolean = true,
)

/**
 * One way to break a cycle: set aside [firstProjectName]'s demand on [waitingProjectName],
 * so the first no longer waits on the second.
 *
 * Phrased as "which comes first" rather than "which edge to delete" because that is the
 * question a person can actually answer about their own world.
 */
data class RoadmapCycleOption(
    /** The consumer of the set-aside edge — freed, so it comes first. */
    val firstProjectId: Int,
    val firstProjectName: String,
    /** The producer of the set-aside edge — still waits on the first. */
    val waitingProjectId: Int,
    val waitingProjectName: String,
    /**
     * What the first project needs from the second, or null when the edge is a declared
     * `project_dependencies` row rather than derived demand — see
     * [ProjectResourceEdge.quantity][app.mcorg.domain.model.project.ProjectResourceEdge.quantity].
     */
    val itemName: String?,
    val quantity: Long?,
)

/** A pair a person has ordered by hand, as stored in `roadmap_cycle_order` (MCO-460). */
data class RoadmapCycleOrder(
    val firstProjectId: Int,
    val firstProjectName: String,
    val waitingProjectId: Int,
    val waitingProjectName: String,
)
