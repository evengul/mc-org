package app.mcorg.domain.model.world

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType

/**
 * Represents a complete roadmap visualization of all projects and their dependencies within a world.
 * This model uses a recursive structure to represent the dependency graph, allowing for efficient
 * traversal and visualization of project relationships.
 *
 * Business Rules:
 * - No circular dependencies are allowed (enforced at creation time)
 * - All dependencies must be within the same world
 * - Projects can have multiple dependencies and dependents
 */
data class Roadmap(
    val worldId: Int,
    val worldName: String,
    val nodes: List<RoadmapNode>,
    val edges: List<RoadmapEdge>,
    val layers: List<RoadmapLayer>
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
    val isBlocking: Boolean
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

