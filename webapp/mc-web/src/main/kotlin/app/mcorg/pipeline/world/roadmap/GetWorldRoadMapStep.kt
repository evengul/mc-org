package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.World
import app.mcorg.domain.model.world.RoadmapCycle
import app.mcorg.domain.model.world.RoadmapCycleOrder
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
import app.mcorg.pipeline.resources.GatheringPlanInput
import app.mcorg.pipeline.resources.GenerateGatheringPlanStep
import app.mcorg.pipeline.resources.GetFarmScaleThresholdStep
import app.mcorg.pipeline.resources.GetWorldDemandCoverageStep
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
 *
 * ## Cycles are real (MCO-460)
 *
 * Derived edges can close a loop out of two true facts, so [RoadmapCycles] detects them
 * explicitly and sets one edge aside per loop before layering. Without that the layering pass
 * left both projects at depth 0 making opposite claims — each shown as blocked by the other —
 * which reads as a bug in the numbers rather than the real ordering question it is.
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

        // Farm edges read materialised demand (MCO-316), which is written wherever a plan is
        // derived — the project page. A project nobody has opened has none, and would silently
        // contribute no edges at all, which is the opposite of "import a build and the roadmap
        // assembles itself". So derive the missing ones here, once each: the write-through in
        // GenerateGatheringPlanStep means the next load finds them stored.
        fillMissingDemand()

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

        // Pairs someone has already ordered by hand. GetFarmSupplyEdgesStep has removed the
        // matching edges, so they are no longer cycles — but the choice has to stay visible, or
        // it could never be changed.
        val resolvedOrders = when (val r = getResolvedOrders()) {
            is Result.Success -> r.value
            is Result.Failure -> emptyList()
        }

        // MCO-401's line, used here only to tell a footnote-sized loop from a real question
        // (MCO-460). Falls back to the default rather than failing the page.
        val farmScaleThreshold = GetFarmScaleThresholdStep.process(worldId).getOrNull()
            ?: World.DEFAULT_FARM_SCALE_THRESHOLD

        return Result.success(buildRoadmap(worldName, projects, edges, resolvedOrders, farmScaleThreshold))
    }

    /**
     * Derives and stores demand for projects that have none yet.
     *
     * Bounded and self-healing: each project costs one derivation *once*, after which the
     * write-through keeps it current. A world where every project has been opened does no work
     * here at all.
     *
     * Failures are swallowed on purpose, per project. A project can legitimately have no plan —
     * everything already collected, or a world whose Minecraft version was never ingested, which
     * is the normal state in tests — and none of that should stop the roadmap rendering. The
     * consequence is a missing edge, not a wrong one.
     */
    private suspend fun fillMissingDemand() {
        val uncovered = when (val r = GetWorldDemandCoverageStep(worldId).process(Unit)) {
            is Result.Success -> r.value
            is Result.Failure -> return
        }
        uncovered.forEach { projectId ->
            GenerateGatheringPlanStep.process(GatheringPlanInput(projectId = projectId, worldId = worldId))
        }
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

    /**
     * The hand-made orderings for this world (MCO-460).
     *
     * Soft-fails to none: losing them means the page offers a question the user has already
     * answered, which is a worse roadmap but still a roadmap.
     */
    private suspend fun getResolvedOrders(): Result<AppFailure.DatabaseError, List<RoadmapCycleOrder>> =
        DatabaseSteps.query<Unit, List<RoadmapCycleOrder>>(
            SafeSQL.select("""
                SELECT
                    o.consumer_project_id AS first_id,
                    first.name            AS first_name,
                    o.producer_project_id AS waiting_id,
                    waiting.name          AS waiting_name
                FROM roadmap_cycle_order o
                JOIN projects first   ON first.id = o.consumer_project_id
                JOIN projects waiting ON waiting.id = o.producer_project_id
                WHERE o.world_id = ?
                ORDER BY first.name
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RoadmapCycleOrder(
                                firstProjectId = rs.getInt("first_id"),
                                firstProjectName = rs.getString("first_name"),
                                waitingProjectId = rs.getInt("waiting_id"),
                                waitingProjectName = rs.getString("waiting_name"),
                            )
                        )
                    }
                }
            }
        ).process(Unit)

    private fun buildRoadmap(
        worldName: String,
        projects: List<ProjectRecord>,
        edges: List<ProjectResourceEdge>,
        resolvedOrders: List<RoadmapCycleOrder>,
        farmScaleThreshold: Int,
    ): Roadmap {
        // Detect loops and set one edge aside per loop, so the page never renders two projects
        // each claiming to block the other (MCO-460).
        val cycles = RoadmapCycles.detect(edges, farmScaleThreshold)
        // Every loop is broken; only the balanced ones are put to the user. A loop the threshold
        // settled still has to be removed from the graph, or what it was breaking comes back.
        val setAside = cycles.mapTo(mutableSetOf()) { it.breaking.firstProjectId to it.breaking.waitingProjectId }

        // **The whole page derives from this one graph.** Layering it but drawing the full set
        // would put Cobblestone Farm at depth 0 while its own row still read "depends on Witch
        // Hut Farm — BLOCKING": two true-looking claims that cannot both hold, which is MCO-318's
        // complaint all over again. The set-aside edge is not lost — the cycle panel above the
        // table names it, and names it as an assumption someone can change.
        val sequencing = edges.filterNot { (it.consumerId to it.producerId) in setAside }

        // Edges reference only projects in this world (every query is world-scoped), but a
        // project may appear in neither map — that just means it is isolated.
        val incoming = sequencing.groupBy { it.consumerId }
        val outgoing = sequencing.groupBy { it.producerId }

        val layers = calculateLayers(projects, sequencing)

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

        val roadmapEdges = sequencing.map { edge ->
            RoadmapEdge(
                fromNodeId = edge.consumerId,
                fromNodeName = edge.consumerName,
                toNodeId = edge.producerId,
                toNodeName = edge.producerName,
                isBlocking = edge.isBlocking,
                itemName = edge.itemName,
                quantity = edge.quantity,
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
            cycles = cycles.filter { it.needsAnAnswer },
            resolvedOrders = resolvedOrders,
        )
    }

    /**
     * Layer depth per project: 0 for projects that depend on nothing, otherwise
     * max(dependency layer) + 1. BFS from the roots, promoting a project only once every
     * one of its dependencies is placed.
     *
     * [edges] arrives acyclic: the caller has already run [RoadmapCycles] and removed one edge
     * per loop. This used to say "cycles are not supposed to exist" and put whatever BFS could
     * not place at depth 0 — which happened constantly, because derived farm edges close loops
     * out of two true facts (MCO-460).
     *
     * The depth-0 fallback below is kept as a floor rather than a diagnosis. If a loop ever
     * survives detection, a roadmap that silently dropped projects would still be worse than
     * one that shows them at the top.
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
