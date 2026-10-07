package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * A project's demand derived *as if* some unfinished farms were producing, cached (MCO-572).
 *
 * The roadmap's panels split "by hand now" into what the farms still to build would take off the
 * list and what stays yours either way. Both halves are differences between two **hand lists** —
 * the plan as the world is, and the plan with those farms built — never sums of supply lines,
 * which mix crafted items in with the raw ones under them and so never add up to a hand list.
 *
 * The second list is a full derivation, ~0.7 s for a 555-target build, so it is stored in
 * `project_demand_scenario` beside the real `project_demand`, never in it.
 *
 * ## When a scenario is stale
 *
 * Exactly when the real demand is. Each stored scenario records the project's own
 * `project_demand_state.fingerprint` from when it was derived, and is trusted only while that is
 * unchanged. A supply change deletes the real fingerprint ([InvalidateDemandSuppliedByStep]); a
 * re-derivation on the project page replaces it. Either way every scenario of that project
 * re-derives on the next roadmap load, and nothing else does — no second invalidation path to keep
 * in step with the first.
 *
 * The one input that fingerprint cannot see is the **assumed farms' own productions**: they are not
 * DONE, so they are not in the real supply map. They are in the [scenarioKeyOf] instead, so editing
 * what an assumed farm makes is a different scenario rather than a stale one.
 */
object ScenarioDemand {

    /**
     * What each project would leave to gather by hand under each scenario, by project and then by
     * assumed set.
     *
     * A scenario that cannot be derived — the project has no real demand yet, or the planner
     * failed — is absent from the result rather than zero: "no number" and "nothing left by hand"
     * read very differently on a panel. A project whose every target is already collected has a
     * hand list of zero under any scenario, and says so.
     *
     * Scenarios a project no longer asks for are deleted as a side effect, so the table holds the
     * current question and nothing older.
     */
    suspend fun handTotals(
        worldId: Int,
        wanted: Map<Int, List<Set<Int>>>,
        /** [GetUnfinishedProductionsStep]'s answer — what each assumed farm makes, for the keys. */
        productions: Map<Int, Set<String>>,
    ): Map<Int, Map<Set<Int>, Long>> {
        val asked = wanted.filterValues { it.isNotEmpty() }
        if (asked.isEmpty()) return emptyMap()

        val cached = GetScenarioHandTotalsStep(asked.keys).process(Unit).getOrNull() ?: return emptyMap()

        return asked.mapValues { (projectId, sets) ->
            val keys = sets.distinct().associateWith { scenarioKeyOf(it, productions) }
            val result = keys.mapNotNull { (assumed, key) ->
                val hit = cached[projectId to key]
                val total = if (hit != null && hit.fresh) hit.byHand else derive(worldId, projectId, assumed, key)
                total?.let { assumed to it }
            }.toMap()
            // Only when something is there to go: a warm roadmap load should write nothing.
            val keep = keys.values.toSet()
            if (cached.keys.any { (project, key) -> project == projectId && key !in keep }) {
                PruneScenariosStep(projectId, keep).process(Unit)
            }
            result
        }
    }

    /**
     * Derives one scenario and stores it against the real demand's current fingerprint. Null when
     * the project has no real demand to anchor it to, or the derivation fails.
     */
    private suspend fun derive(worldId: Int, projectId: Int, assumed: Set<Int>, key: String): Long? {
        // Read before deriving: if the real demand changes mid-derivation, the scenario is stored
        // against the older fingerprint and simply re-derives next time.
        val base = GetStoredDemandFingerprintStep(projectId).process(Unit).getOrNull() ?: return null

        // Off the call thread: a derivation is ~0.65 s of planner work, a numbered order asks for
        // one per step, and production has a single call thread for every request (MCO-551).
        val generated = withContext(Dispatchers.Default) {
            GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId, assumed))
        }
        val plan = when (val r = generated) {
            is Result.Success -> r.value
            // Every target collected: nothing is left by hand whatever is built.
            is Result.Failure -> if (r.error is AppFailure.ValidationError) null else return null
        }

        val saved = SaveScenarioDemandStep(projectId, key, base).process(plan)
        if (saved is Result.Failure) {
            // No exception and no row data: see documentation/logging.md.
            logger.warn("Could not store scenario demand for project {}", projectId)
        }
        return plan?.handTotal() ?: 0L
    }

    private fun GatheringPlan.handTotal(): Long =
        activityList.filter { it.status == PlanNodeStatus.RAW_GATHER }.sumOf { it.quantity }

    /**
     * The scenario's identity: which farms are assumed built and what each of them makes.
     *
     * The productions are in the key because the real fingerprint cannot see them — an assumed
     * farm is not DONE, so its output is not in the supply map that fingerprint hashes.
     */
    internal fun scenarioKeyOf(assumed: Set<Int>, productions: Map<Int, Set<String>>): String {
        val payload = assumed.sorted().joinToString("\n") { id ->
            "$id:" + productions[id].orEmpty().sorted().joinToString(",")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest("v1\n$payload".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val logger = LoggerFactory.getLogger(ScenarioDemand::class.java)
}

/**
 * Every unfinished or stopped project's productions in [worldId], by project: what a scenario would
 * assume built, for its key, and what the roadmap matches stopped farms against.
 */
data class GetUnfinishedProductionsStep(val worldId: Int) :
    Step<Unit, AppFailure.DatabaseError, Map<Int, Set<String>>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Map<Int, Set<String>>> =
        DatabaseSteps.query<Unit, Map<Int, Set<String>>>(
            sql = SafeSQL.select(
                """
                SELECT pp.project_id, pp.item_id
                FROM project_productions pp
                JOIN projects p ON p.id = pp.project_id
                WHERE p.world_id = ?
                  AND p.state <> ?
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setString(2, ProjectState.DONE.name)
            },
            resultMapper = { rs ->
                val byProject = mutableMapOf<Int, MutableSet<String>>()
                while (rs.next()) byProject.getOrPut(rs.getInt("project_id")) { mutableSetOf() }.add(rs.getString("item_id"))
                byProject
            },
        ).process(Unit)
}

/** A stored scenario's hand total, and whether it still matches the real demand it was derived beside. */
data class CachedScenario(val byHand: Long, val fresh: Boolean)

/** Every stored scenario of [projectIds], keyed by (project, scenario key). */
data class GetScenarioHandTotalsStep(val projectIds: Set<Int>) :
    Step<Unit, AppFailure.DatabaseError, Map<Pair<Int, String>, CachedScenario>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Map<Pair<Int, String>, CachedScenario>> =
        DatabaseSteps.query<Unit, Map<Pair<Int, String>, CachedScenario>>(
            sql = SafeSQL.select(
                """
                SELECT
                  s.project_id,
                  s.scenario_key,
                  (m.fingerprint IS NOT NULL AND m.fingerprint = s.base_fingerprint) AS fresh,
                  COALESCE((
                    SELECT SUM(r.quantity)
                    FROM project_demand_scenario r
                    WHERE r.project_id = s.project_id
                      AND r.scenario_key = s.scenario_key
                      AND r.node_status = 'RAW_GATHER'
                  ), 0) AS by_hand
                FROM project_demand_scenario_state s
                LEFT JOIN project_demand_state m ON m.project_id = s.project_id
                WHERE s.project_id = ANY(?)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setArray(1, statement.connection.createArrayOf("integer", projectIds.toTypedArray()))
            },
            resultMapper = { rs ->
                buildMap {
                    while (rs.next()) {
                        put(
                            rs.getInt("project_id") to rs.getString("scenario_key"),
                            CachedScenario(byHand = rs.getLong("by_hand"), fresh = rs.getBoolean("fresh")),
                        )
                    }
                }
            },
        ).process(Unit)
}

/**
 * Replaces one scenario of one project with what a plan just derived — or with nothing, when every
 * target is collected. One transaction, so a reader never sees half a scenario.
 */
data class SaveScenarioDemandStep(
    val projectId: Int,
    val scenarioKey: String,
    val baseFingerprint: DemandFingerprint,
) : Step<GatheringPlan?, AppFailure.DatabaseError, Unit> {

    override suspend fun process(input: GatheringPlan?): Result<AppFailure.DatabaseError, Unit> {
        val rows = input?.activityList.orEmpty()
        return DatabaseSteps.transaction { connection ->
            object : Step<Unit, AppFailure.DatabaseError, Unit> {
                override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Unit> {
                    // The rows cascade with their state row.
                    val cleared = DatabaseSteps.update<Unit>(
                        sql = SafeSQL.delete(
                            "DELETE FROM project_demand_scenario_state WHERE project_id = ? AND scenario_key = ?"
                        ),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, projectId)
                            statement.setString(2, scenarioKey)
                        },
                        transactionConnection = connection,
                    ).process(Unit)
                    if (cleared is Result.Failure) return Result.Failure(cleared.error)

                    val stamped = DatabaseSteps.update<Unit>(
                        sql = SafeSQL.insert(
                            """
                            INSERT INTO project_demand_scenario_state (project_id, scenario_key, base_fingerprint, derived_at)
                            VALUES (?, ?, ?, now())
                            """.trimIndent()
                        ),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, projectId)
                            statement.setString(2, scenarioKey)
                            statement.setString(3, baseFingerprint.value)
                        },
                        transactionConnection = connection,
                    ).process(Unit)
                    if (stamped is Result.Failure) return Result.Failure(stamped.error)

                    if (rows.isEmpty()) return Result.success(Unit)
                    val inserted = DatabaseSteps.batchUpdate<GatheringPlanActivityRow>(
                        SafeSQL.insert(
                            """
                            INSERT INTO project_demand_scenario
                                (project_id, scenario_key, item_id, item_name, quantity, activity_group, node_status)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ),
                        parameterSetter = { statement, row ->
                            statement.setInt(1, projectId)
                            statement.setString(2, scenarioKey)
                            statement.setString(3, row.itemId)
                            statement.setString(4, row.itemName)
                            statement.setLong(5, row.quantity)
                            statement.setString(6, row.group)
                            statement.setString(7, row.status)
                        },
                        transactionConnection = connection,
                    ).process(
                        rows.map {
                            GatheringPlanActivityRow(it.item.id, it.item.name, it.quantity, it.group.name, it.status.name)
                        }
                    )
                    if (inserted is Result.Failure) return Result.Failure(inserted.error)
                    return Result.success(Unit)
                }
            }
        }.process(Unit)
    }
}

/** One activity of a plan, flattened for a batch insert. */
data class GatheringPlanActivityRow(
    val itemId: String,
    val itemName: String,
    val quantity: Long,
    val group: String,
    val status: String,
)

/** Deletes every scenario of [projectId] that is not in [keep] — the questions nobody asks any more. */
data class PruneScenariosStep(val projectId: Int, val keep: Set<String>) : Step<Unit, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete(
                "DELETE FROM project_demand_scenario_state WHERE project_id = ? AND NOT (scenario_key = ANY(?))"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setArray(2, statement.connection.createArrayOf("text", keep.toTypedArray()))
            },
        ).process(Unit)
}
