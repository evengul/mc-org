package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * The two things the graph view needs that [GetWorldRoadMapStep] does not carry (MCO-469):
 * the terminal project's plan totals, and what is left to gather by hand.
 *
 * Producers are **not** read here — they roll up out of [Roadmap.edges], which already carries
 * per-edge quantities (MCO-316) and already agrees with what the table view prints. Querying
 * them again would be a second derivation of the same fact, which is the class of bug MCO-461
 * and MCO-466 were both filed for.
 *
 * Decorates a page that already renders: every failure degrades to "no numbers on the terminal
 * panel", never to no page. Same posture as the plan's own decorations.
 */
data class GetRoadmapGraphDataStep(val terminalProjectId: Int) :
    Step<Unit, AppFailure.DatabaseError, RoadmapGraphData> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, RoadmapGraphData> =
        DatabaseSteps.query<Unit, RoadmapGraphData>(
            sql = SafeSQL.select(
                """
                SELECT
                  COALESCE(SUM(d.quantity) FILTER (WHERE d.node_status = 'SUPPLIED'), 0)    AS from_farms,
                  COALESCE(SUM(d.quantity) FILTER (WHERE d.node_status = 'RAW_GATHER'), 0)  AS by_hand,
                  COUNT(*) FILTER (WHERE d.node_status = 'RAW_GATHER')                      AS hand_materials,
                  COUNT(*) FILTER (WHERE d.activity_group = 'CRAFT')                        AS craft_rows,
                  COUNT(*) FILTER (WHERE d.activity_group = 'NEEDS_ATTENTION')              AS open_questions
                FROM project_demand d
                WHERE d.project_id = ?
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, terminalProjectId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    RoadmapGraphData(
                        fromFarms = rs.getLong("from_farms"),
                        byHand = rs.getLong("by_hand"),
                        handMaterials = rs.getInt("hand_materials"),
                        craftRows = rs.getInt("craft_rows"),
                        openQuestions = rs.getInt("open_questions"),
                    )
                } else {
                    RoadmapGraphData.EMPTY
                }
            }
        ).process(input)
}

data class RoadmapGraphData(
    val fromFarms: Long,
    val byHand: Long,
    val handMaterials: Int,
    val craftRows: Int,
    val openQuestions: Int,
) {
    companion object {
        val EMPTY = RoadmapGraphData(0, 0, 0, 0, 0)
    }
}

/**
 * The two largest hand-gathered materials, for the concentration warning.
 *
 * Two and not more: the warning exists to name what a farm would remove, and a list of five
 * names stops being a warning and becomes a table. See [concentrationOf].
 */
data class GetTopHandMaterialsStep(val terminalProjectId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<Pair<String, Long>>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<Pair<String, Long>>> =
        DatabaseSteps.query<Unit, List<Pair<String, Long>>>(
            sql = SafeSQL.select(
                """
                SELECT d.item_name, d.quantity
                FROM project_demand d
                WHERE d.project_id = ?
                  AND d.node_status = 'RAW_GATHER'
                ORDER BY d.quantity DESC
                LIMIT 2
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, terminalProjectId) },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString("item_name") to rs.getLong("quantity"))
                    }
                }
            }
        ).process(input)
}

/**
 * How much of the terminal project's declared gathering is actually collected.
 *
 * Separate from [GetRoadmapGraphDataStep] because it reads declared rows rather than derived
 * demand: progress is recorded against `resource_gathering`, which is what the Execute view
 * counts, so the percentage here and the one on the project page come from one source.
 */
data class GetTerminalProgressStep(val terminalProjectId: Int) :
    Step<Unit, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select(
                """
                SELECT
                  COALESCE(SUM(rg.required), 0)                                  AS required,
                  COALESCE(SUM(LEAST(COALESCE(p.collected, 0), rg.required)), 0)  AS collected
                FROM resource_gathering rg
                LEFT JOIN resource_gathering_progress p
                       ON p.project_id = rg.project_id AND p.item_id = rg.item_id
                WHERE rg.project_id = ?
                  AND rg.ignored = FALSE
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, terminalProjectId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    val required = rs.getLong("required")
                    val collected = rs.getLong("collected")
                    if (required > 0) ((collected * 100) / required).toInt() else 0
                } else {
                    0
                }
            }
        ).process(input)
}

/**
 * Finished farms feeding [terminalProjectId], rolled up per producer.
 *
 * Reads the roadmap's own edges rather than the database — see the note on
 * [GetRoadmapGraphDataStep]. Only `DONE` producers qualify: the supply column's claim is that
 * nothing in it is waiting on anything, so an unfinished farm belongs in the sequence band
 * instead, however much it will eventually produce.
 */
fun producersOf(roadmap: Roadmap, terminalProjectId: Int): List<RoadmapGraphLayout.Producer> =
    rollUpProducers(roadmap) { it.fromNodeId == terminalProjectId }

/**
 * Every finished producer in the world, whoever it feeds.
 *
 * The graph column is necessarily about one terminal project, but the *page* must not lose
 * projects because of that choice: a world with two independent builds has farms feeding the
 * one the graph did not pick, and they would otherwise appear in no section at all. The
 * "Producing" grid is therefore a superset of the column, which is also the honest reading of
 * its heading — these are the farms that are done and still feeding the roadmap.
 */
fun allProducersOf(roadmap: Roadmap): List<RoadmapGraphLayout.Producer> =
    rollUpProducers(roadmap) { true }

/**
 * Rolls matching edges up per producing project.
 *
 * Only `DONE` producers qualify: the supply column's claim is that nothing in it is waiting on
 * anything, so an unfinished farm belongs in the sequence band instead, however much it will
 * eventually produce.
 */
private fun rollUpProducers(
    roadmap: Roadmap,
    matches: (app.mcorg.domain.model.world.RoadmapEdge) -> Boolean,
): List<RoadmapGraphLayout.Producer> {
    val doneIds = roadmap.nodes
        .filter { it.state == ProjectState.DONE }
        .mapTo(mutableSetOf()) { it.projectId }

    return roadmap.edges
        .filter(matches)
        .filter { it.toNodeId in doneIds }
        .groupBy { it.toNodeId }
        .map { (producerId, producerEdges) ->
            val largest = producerEdges
                .filter { it.itemName != null }
                .maxByOrNull { it.quantity ?: Long.MIN_VALUE }
            RoadmapGraphLayout.Producer(
                projectId = producerId,
                name = producerEdges.first().toNodeName,
                items = producerEdges.sumOf { it.quantity ?: 0L },
                edges = producerEdges.size,
                largestItemName = largest?.itemName,
                largestItemQuantity = largest?.quantity,
            )
        }
}

/**
 * The concentration warning on the by-hand node — "Oak Log + Ice = 84%".
 *
 * Hand gathering is almost always dominated by two or three materials (on the YAMS import,
 * four of ninety-four are 98% of it), and that concentration is the actionable fact: it is
 * what a tree farm or an ice farm would remove. Null when nothing dominates.
 */
fun concentrationOf(top: List<Pair<String, Long>>, total: Long): String? {
    if (total <= 0 || top.isEmpty()) return null
    val leading = top.take(2)
    val share = (leading.sumOf { it.second } * 100) / total
    if (share < 50) return null
    return "${leading.joinToString(" + ") { shortItemName(it.first) }} = $share%"
}

/**
 * "Oak Log (Block)" → "Oak Log".
 *
 * The `(Block)` / `(Item)` suffix is the app's display convention everywhere else, but this
 * line has 216px and one line to work with — with the suffixes the warning wraps to two lines
 * and is clipped by the node it sits in. The disambiguation earns nothing here: nobody reads
 * "Oak Log + Ice = 84%" and wonders which kind of ice.
 */
private fun shortItemName(name: String): String =
    name.removeSuffix(" (Block)").removeSuffix(" (Item)")
