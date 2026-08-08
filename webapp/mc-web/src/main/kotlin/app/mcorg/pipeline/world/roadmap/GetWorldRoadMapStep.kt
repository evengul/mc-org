package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep
import app.mcorg.pipeline.project.commonsteps.GetProjectEdgesStep
import java.sql.ResultSet

/**
 * Builds the world's project dependency roadmap (MCO-288).
 *
 * **The roadmap is derived, never hand-curated.** Its edges come from three places, all of
 * them consequences of what the user actually declared about resources:
 *
 * 1. item-level links — a requirement solved by another project (`solved_by_project_id`)
 * 2. farm supply — a requirement another project *produces* ([GetFarmSupplyEdgesStep], MCO-287)
 * 3. manual project→project sequencing rows (`project_dependencies`), for the non-resource
 *    ordering that no item can express ("dig the perimeter first")
 *
 * The first and third arrive together from [GetProjectEdgesStep], which the Field Log already
 * uses — so the roadmap and the Field Log cannot disagree about what blocks what.
 *
 * Blocking is a function of the producer's [ProjectState]: not DONE means still blocking.
 * This is the same rule as [ProjectResourceEdge.isBlocking] and the same one the planner
 * uses to decide whether a farm supplies anything yet.
 */
data class GetWorldRoadMapStep(val worldId: Int) : Step<Unit, AppFailure, Roadmap> {

    override suspend fun process(input: Unit): Result<AppFailure, Roadmap> {
        val worldName = when (val r = getWorldName()) {
            is Result.Success -> r.value ?: return Result.failure(AppFailure.DatabaseError.NotFound)
            is Result.Failure -> return r
        }

        val projects = when (val r = getProjects()) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        val declaredEdges = when (val r = GetProjectEdgesStep(worldId).process(Unit)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        val farmEdges = when (val r = GetFarmSupplyEdgesStep(worldId).process(Unit)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }

        // Same (consumer, producer, item) can arrive from more than one source; the roadmap
        // shows the relationship once.
        val edges = (declaredEdges + farmEdges)
            .distinctBy { Triple(it.consumerId, it.producerId, it.itemName) }

        return Result.success(buildRoadmap(worldName, projects, edges))
    }

    private suspend fun getWorldName(): Result<AppFailure.DatabaseError, String?> =
        DatabaseSteps.query<Unit, String?>(
            sql = SafeSQL.select("SELECT name FROM world WHERE id = ?"),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { if (it.next()) it.getString("name") else null }
        ).process(Unit)

    private suspend fun getProjects(): Result<AppFailure.DatabaseError, List<ProjectRecord>> =
        DatabaseSteps.query<Unit, List<ProjectRecord>>(
            SafeSQL.select("""
                SELECT
                    p.id,
                    p.name,
                    p.type,
                    p.stage,
                    p.state,
                    COUNT(t.id)                                AS tasks_total,
                    COUNT(t.id) FILTER (WHERE t.completed)     AS tasks_completed
                FROM projects p
                LEFT JOIN action_task t ON t.project_id = p.id
                WHERE p.world_id = ?
                GROUP BY p.id, p.name, p.type, p.stage, p.state
                ORDER BY p.name
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { it.toProjectRecords() }
        ).process(Unit)

    private fun buildRoadmap(
        worldName: String,
        projects: List<ProjectRecord>,
        edges: List<ProjectResourceEdge>,
    ): Roadmap {
        // Edges reference only projects in this world (every query is world-scoped), but a
        // project may appear in neither map — that just means it is isolated.
        val incoming = edges.groupBy { it.consumerId }
        val outgoing = edges.groupBy { it.producerId }

        val layers = calculateLayers(projects, edges)

        val nodes = projects.map { project ->
            val dependencies = incoming[project.id].orEmpty()
            val blocking = dependencies.filter { it.isBlocking }

            RoadmapNode(
                projectId = project.id,
                projectName = project.name,
                projectType = project.type,
                stage = project.stage,
                state = project.state,
                tasksTotal = project.tasksTotal,
                tasksCompleted = project.tasksCompleted,
                isBlocked = blocking.isNotEmpty(),
                blockingProjectIds = blocking.map { it.producerId }.distinct(),
                dependentProjectIds = outgoing[project.id].orEmpty().map { it.consumerId }.distinct(),
                layer = layers[project.id] ?: 0,
            )
        }

        val roadmapEdges = edges.map { edge ->
            RoadmapEdge(
                fromNodeId = edge.consumerId,
                fromNodeName = edge.consumerName,
                toNodeId = edge.producerId,
                toNodeName = edge.producerName,
                isBlocking = edge.isBlocking,
                itemName = edge.itemName,
            )
        }

        val layerGroups = layers.entries
            .groupBy { it.value }
            .map { (depth, entries) ->
                val projectIds = entries.map { it.key }
                RoadmapLayer(depth = depth, projectIds = projectIds, projectCount = projectIds.size)
            }
            .sortedBy { it.depth }

        return Roadmap(
            worldId = worldId,
            worldName = worldName,
            nodes = nodes,
            edges = roadmapEdges,
            layers = layerGroups,
        )
    }

    /**
     * Layer depth per project: 0 for projects that depend on nothing, otherwise
     * max(dependency layer) + 1. BFS from the roots, promoting a project only once every
     * one of its dependencies is placed.
     *
     * Anything left unplaced is in a dependency cycle. Cycles are not supposed to exist,
     * but a roadmap that silently dropped projects would be worse than one that shows them
     * at depth 0 — so they land at the top, visible.
     */
    private fun calculateLayers(
        projects: List<ProjectRecord>,
        edges: List<ProjectResourceEdge>,
    ): Map<Int, Int> {
        val layers = mutableMapOf<Int, Int>()
        val dependenciesOf = edges.groupBy { it.consumerId }
        val dependentsOf = edges.groupBy { it.producerId }
        val placed = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()

        projects.filter { it.id !in dependenciesOf }.forEach { project ->
            layers[project.id] = 0
            placed.add(project.id)
            queue.add(project.id)
        }

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            dependentsOf[currentId].orEmpty().forEach { edge ->
                val dependentId = edge.consumerId
                if (dependentId in placed) return@forEach
                val dependencies = dependenciesOf[dependentId].orEmpty()
                if (dependencies.all { it.producerId in placed }) {
                    layers[dependentId] = (dependencies.maxOfOrNull { layers[it.producerId] ?: 0 } ?: 0) + 1
                    placed.add(dependentId)
                    queue.add(dependentId)
                }
            }
        }

        projects.forEach { project -> layers.putIfAbsent(project.id, 0) }
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
                    state = ProjectState.valueOf(getString("state")),
                    tasksTotal = getInt("tasks_total"),
                    tasksCompleted = getInt("tasks_completed"),
                )
            )
        }
    }

    private data class ProjectRecord(
        val id: Int,
        val name: String,
        val type: ProjectType,
        val stage: ProjectStage,
        val state: ProjectState,
        val tasksTotal: Int,
        val tasksCompleted: Int,
    )
}
