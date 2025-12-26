package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.sql.ResultSet

data class GetWorldRoadMapStep(val worldId: Int) : Step<Unit, AppFailure, Roadmap> {
    override suspend fun process(input: Unit): Result<AppFailure, Roadmap> {
        // Step 1: Get all projects in the world with task counts
        val projects = when (val projectsResult = getProjects()) {
            is Result.Success -> projectsResult.value
            is Result.Failure -> return projectsResult
        }

        // Step 2: Get all dependencies
        val dependencies = when (val dependenciesResult = getDependencies()) {
            is Result.Success -> dependenciesResult.value
            is Result.Failure -> return dependenciesResult
        }

        // Step 3: Build the roadmap structure
        val roadmap = buildRoadmap(projects, dependencies)

        return Result.success(roadmap)
    }

    private suspend fun getProjects(): Result<AppFailure.DatabaseError, List<ProjectRecord>> {
        return DatabaseSteps.query<Unit, List<ProjectRecord>>(
            SafeSQL.select("""
                SELECT 
                    p.id,
                    p.name,
                    p.type,
                    p.stage,
                    p.world_id,
                    COUNT(t.id) as tasks_total,
                    COUNT(CASE WHEN t.completed = TRUE THEN 1 ELSE 0 END) as tasks_completed
                FROM projects p
                LEFT JOIN action_task t ON t.project_id = p.id
                WHERE p.world_id = ?
                GROUP BY p.id, p.name, p.type, p.stage, p.world_id
                ORDER BY p.name
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
            },
            resultMapper = { it.toProjectRecords() }
        ).process(Unit)
    }

    private suspend fun getDependencies(): Result<AppFailure.DatabaseError, List<DependencyRecord>> {
        return DatabaseSteps.query<Unit, List<DependencyRecord>>(
            SafeSQL.select("""
                SELECT 
                    pd.project_id as from_id,
                    p1.name as from_name,
                    pd.depends_on_project_id as to_id,
                    p2.name as to_name,
                    p2.stage as to_stage
                FROM project_dependencies pd
                JOIN projects p1 ON pd.project_id = p1.id
                JOIN projects p2 ON pd.depends_on_project_id = p2.id
                WHERE p1.world_id = ?
                ORDER BY pd.project_id, pd.depends_on_project_id
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
            },
            resultMapper = { it.toDependencyRecords() }
        ).process(Unit)
    }

    private fun buildRoadmap(projects: List<ProjectRecord>, dependencies: List<DependencyRecord>): Roadmap {
        // Build dependency maps
        val dependencyMap = dependencies.groupBy { it.fromId }
        val dependentMap = dependencies.groupBy { it.toId }

        // Calculate layers using topological sort
        val layers = calculateLayers(projects, dependencies)

        // Get world name from first project (or use default)
        val worldName = projects.firstOrNull()?.let { "World ${it.worldId}" } ?: "Empty World"

        // Build nodes
        val nodes = projects.map { project ->
            val projectDependencies = dependencyMap[project.id] ?: emptyList()
            val projectDependents = dependentMap[project.id] ?: emptyList()

            // A project is blocked if any of its dependencies are not completed
            val blockingProjects = projectDependencies.filter { it.toStage != ProjectStage.COMPLETED }
            val isBlocked = blockingProjects.isNotEmpty()

            RoadmapNode(
                projectId = project.id,
                projectName = project.name,
                projectType = project.type,
                stage = project.stage,
                tasksTotal = project.tasksTotal,
                tasksCompleted = project.tasksCompleted,
                isBlocked = isBlocked,
                blockingProjectIds = blockingProjects.map { it.toId },
                dependentProjectIds = projectDependents.map { it.fromId },
                layer = layers[project.id] ?: 0
            )
        }

        // Build edges
        val edges = dependencies.map { dep ->
            val isBlocking = dep.toStage != ProjectStage.COMPLETED
            RoadmapEdge(
                fromNodeId = dep.fromId,
                fromNodeName = dep.fromName,
                toNodeId = dep.toId,
                toNodeName = dep.toName,
                isBlocking = isBlocking
            )
        }

        // Build layer groups
        val layerGroups = layers.entries
            .groupBy { it.value }
            .map { (depth, entries) ->
                val projectIds = entries.map { it.key }
                RoadmapLayer(
                    depth = depth,
                    projectIds = projectIds,
                    projectCount = projectIds.size
                )
            }
            .sortedBy { it.depth }

        return Roadmap(
            worldId = worldId,
            worldName = worldName,
            nodes = nodes,
            edges = edges,
            layers = layerGroups
        )
    }

    /**
     * Calculates the layer depth for each project using topological sort.
     * Layer 0 = projects with no dependencies
     * Layer N = projects whose dependencies are all in layers 0..N-1
     */
    private fun calculateLayers(projects: List<ProjectRecord>, dependencies: List<DependencyRecord>): Map<Int, Int> {
        val layers = mutableMapOf<Int, Int>()
        val dependencyMap = dependencies.groupBy { it.fromId }
        val processed = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()

        // Find root projects (no dependencies)
        val projectsWithDeps = dependencyMap.keys
        val rootProjects = projects.filter { it.id !in projectsWithDeps }

        // Initialize root projects at layer 0
        rootProjects.forEach { project ->
            layers[project.id] = 0
            processed.add(project.id)
            queue.add(project.id)
        }

        // Process queue using BFS
        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()

            // Find all projects that depend on this one
            val dependents = dependencies.filter { it.toId == currentId }

            dependents.forEach { dep ->
                val dependentId = dep.fromId

                // Check if all dependencies of the dependent project are processed
                val allDeps = dependencyMap[dependentId] ?: emptyList()
                val allDepsProcessed = allDeps.all { it.toId in processed }

                if (allDepsProcessed && dependentId !in processed) {
                    // Calculate the layer as max(dependency layers) + 1
                    val maxDepLayer = allDeps.maxOfOrNull { layers[it.toId] ?: 0 } ?: 0
                    layers[dependentId] = maxDepLayer + 1
                    processed.add(dependentId)
                    queue.add(dependentId)
                }
            }
        }

        // Handle any unprocessed projects (shouldn't happen with valid data, but safety first)
        projects.forEach { project ->
            if (project.id !in layers) {
                layers[project.id] = 0
            }
        }

        return layers
    }

    private fun ResultSet.toProjectRecords() = buildList {
        while (next()) {
            add(
                ProjectRecord(
                    id = getInt("id"),
                    name = getString("name"),
                    type = ProjectType.valueOf(getString("type")),
                    stage = ProjectStage.valueOf(getString("stage")),
                    worldId = getInt("world_id"),
                    tasksTotal = getInt("tasks_total"),
                    tasksCompleted = getInt("tasks_completed")
                )
            )
        }
    }

    private fun ResultSet.toDependencyRecords() = buildList {
        while (next()) {
            add(
                DependencyRecord(
                    fromId = getInt("from_id"),
                    fromName = getString("from_name"),
                    toId = getInt("to_id"),
                    toName = getString("to_name"),
                    toStage = ProjectStage.valueOf(getString("to_stage"))
                )
            )
        }
    }

    private data class ProjectRecord(
        val id: Int,
        val name: String,
        val type: ProjectType,
        val stage: ProjectStage,
        val worldId: Int,
        val tasksTotal: Int,
        val tasksCompleted: Int
    )

    private data class DependencyRecord(
        val fromId: Int,
        val fromName: String,
        val toId: Int,
        val toName: String,
        val toStage: ProjectStage
    )
}
