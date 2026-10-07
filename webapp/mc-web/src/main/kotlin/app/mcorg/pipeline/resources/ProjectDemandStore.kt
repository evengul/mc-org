package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectDemand
import app.mcorg.pipeline.Step
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.security.MessageDigest
import java.time.Instant

/**
 * The materialised per-project demand view (MCO-316) — writing it, and knowing when it is stale.
 *
 * Reading it is [GetWorldDemandStep]. The two halves are split because they run on different
 * pages: demand is *written* wherever a plan gets derived anyway (the project page), and *read*
 * by the roadmap, which must not derive a plan per project to draw a table.
 */

/** What a project's demand was derived from, hashed. Cheap to recompute; the planner is not. */
data class DemandFingerprint(val value: String) {
    companion object {
        /**
         * The revision of the derivation itself: the planner, its cost model, and what this
         * fingerprint hashes. None of those are data, so no input can notice them change.
         *
         * **Bump it when a change to `mc-engine`'s selection or costing, or to
         * [GenerateGatheringPlanStep], changes what a stored plan would contain.** Every stored
         * plan then re-derives, lazily, the next time its world's roadmap is opened
         * ([GetWorldDemandCoverageStep] reads the stored revision) or its project page is (the
         * revision is hashed below). A change that cannot alter a plan — a refactor, a new
         * diagnostic — needs no bump, and a bump costs one derivation per planned project.
         *
         * History: 2 when the world's wood species joined the inputs (MCO-409); 3 when the
         * version's ingestion epoch did, and the revision started being stored (MCO-578).
         */
        const val REVISION: Int = 3

        /**
         * Hashes every input [GenerateGatheringPlanStep] reads.
         *
         * Order is normalised so two runs over the same state agree. The world's farm supply is
         * in here as well as the project's own rows, which means marking any farm DONE
         * invalidates every other project's stored demand — correct, since that is exactly when
         * their chains stop expanding past the supplied item. The world's wood species is in for
         * the same reason: changing which tree you farm changes what every wood tag resolves to.
         *
         * [gameDataEpoch] is when the version's game data was last ingested. A re-ingest of the
         * same version (`FORCE_REINGEST`, a bumped `ExtractionVersion`) changes the recipes and
         * loot tables under a plan without changing the version string, so the string alone would
         * call the old plan current.
         */
        fun of(
            worldVersion: String,
            targets: List<Triple<String, Long, String?>>,
            supplied: Map<String, String>,
            overrides: List<Pair<String, String>>,
            woodSpecies: String? = null,
            gameDataEpoch: Instant? = null,
        ): DemandFingerprint {
            val payload = buildString {
                append('v').append(REVISION).append('|')
                append(worldVersion).append('@').append(gameDataEpoch?.toEpochMilli() ?: "-").append('|')
                append(woodSpecies ?: "-").append('\n')
                targets.sortedBy { it.first }.forEach { (id, amount, source) ->
                    append(id).append('=').append(amount).append(':').append(source ?: "-").append('\n')
                }
                supplied.entries.sortedBy { it.key }.forEach { (id, label) ->
                    append('s').append(id).append('=').append(label).append('\n')
                }
                overrides.sortedBy { it.first }.forEach { (key, value) ->
                    append('o').append(key).append('=').append(value).append('\n')
                }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
            return DemandFingerprint(digest.joinToString("") { "%02x".format(it) })
        }
    }
}

/** The fingerprint stored against a project, or null when nothing has been derived yet. */
data class GetStoredDemandFingerprintStep(val projectId: Int) :
    Step<Unit, AppFailure.DatabaseError, DemandFingerprint?> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, DemandFingerprint?> =
        DatabaseSteps.query<Unit, DemandFingerprint?>(
            sql = SafeSQL.select("SELECT fingerprint FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { statement, _ -> statement.setInt(1, projectId) },
            resultMapper = { if (it.next()) DemandFingerprint(it.getString("fingerprint")) else null },
        ).process(Unit)
}

/**
 * Replaces a project's stored demand with what the plan just derived.
 *
 * Delete-then-insert rather than upsert: an item that has left the plan entirely must leave the
 * table with it, and a diff would have to find those anyway. The whole thing is one transaction,
 * so a reader never sees a half-written project.
 *
 * Every activity is stored, tags included. A tag can never match a production row — you cannot
 * produce `#minecraft:planks` — but filtering here would make the table a view of one consumer's
 * needs rather than the derivation itself, and MCO-401 wants a different slice of it.
 */
data class SaveProjectDemandStep(
    val projectId: Int,
    val fingerprint: DemandFingerprint,
) : Step<GatheringPlan, AppFailure.DatabaseError, Unit> {

    override suspend fun process(input: GatheringPlan): Result<AppFailure.DatabaseError, Unit> {
        val rows = input.activityList.map { activity ->
            ProjectDemand(
                projectId = projectId,
                itemId = activity.item.id,
                itemName = activity.item.name,
                quantity = activity.quantity,
                group = activity.group.name,
                status = activity.status.name,
            )
        }

        return DatabaseSteps.transaction { connection ->
            object : Step<List<ProjectDemand>, AppFailure.DatabaseError, Unit> {
                override suspend fun process(
                    input: List<ProjectDemand>,
                ): Result<AppFailure.DatabaseError, Unit> {
                    val cleared = DatabaseSteps.update<List<ProjectDemand>>(
                        sql = SafeSQL.delete("DELETE FROM project_demand WHERE project_id = ?"),
                        parameterSetter = { statement, _ -> statement.setInt(1, projectId) },
                        transactionConnection = connection,
                    ).process(input)
                    if (cleared is Result.Failure) return Result.Failure(cleared.error)

                    val inserted = DatabaseSteps.batchUpdate<ProjectDemand>(
                        SafeSQL.insert(
                            """
                            INSERT INTO project_demand
                                (project_id, item_id, item_name, quantity, activity_group, node_status)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ),
                        parameterSetter = { statement, row ->
                            statement.setInt(1, row.projectId)
                            statement.setString(2, row.itemId)
                            statement.setString(3, row.itemName)
                            statement.setLong(4, row.quantity)
                            statement.setString(5, row.group)
                            statement.setString(6, row.status)
                        },
                        transactionConnection = connection,
                    ).process(input)
                    if (inserted is Result.Failure) return Result.Failure(inserted.error)

                    val stamped = DatabaseSteps.update<List<ProjectDemand>>(
                        sql = SafeSQL.insert(
                            """
                            INSERT INTO project_demand_state (project_id, fingerprint, derived_at, revision)
                            VALUES (?, ?, now(), ?)
                            ON CONFLICT (project_id)
                            DO UPDATE SET fingerprint = EXCLUDED.fingerprint, derived_at = EXCLUDED.derived_at,
                                          revision = EXCLUDED.revision
                            """.trimIndent()
                        ),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, projectId)
                            statement.setString(2, fingerprint.value)
                            statement.setInt(3, DemandFingerprint.REVISION)
                        },
                        transactionConnection = connection,
                    ).process(input)
                    if (stamped is Result.Failure) return Result.Failure(stamped.error)

                    return Result.success(Unit)
                }
            }
        }.process(rows)
    }
}

/**
 * Every project's stored demand for one world, for the roadmap.
 *
 * Projects whose demand has never been derived simply have no rows. That is visible rather than
 * wrong: the roadmap draws the edges it can prove, and opening the project derives and stores
 * the rest. See [GetWorldDemandCoverageStep] for what the screen can say about the gap.
 */
data class GetWorldDemandStep(val worldId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<ProjectDemand>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectDemand>> =
        DatabaseSteps.query<Unit, List<ProjectDemand>>(
            sql = SafeSQL.select(
                """
                SELECT d.project_id, d.item_id, d.item_name, d.quantity, d.activity_group, d.node_status
                FROM project_demand d
                JOIN projects p ON p.id = d.project_id
                WHERE p.world_id = ?
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            ProjectDemand(
                                projectId = resultSet.getInt("project_id"),
                                itemId = resultSet.getString("item_id"),
                                itemName = resultSet.getString("item_name"),
                                quantity = resultSet.getLong("quantity"),
                                group = resultSet.getString("activity_group"),
                                status = resultSet.getString("node_status"),
                            )
                        )
                    }
                }
            },
        ).process(Unit)
}

/**
 * Which projects in a world have no *current* derived demand stored — the roadmap's fill-on-read
 * list. Projects with nothing to gather at all are excluded: they are not waiting on a
 * derivation, they simply have no requirements.
 *
 * A stored derivation counts only when it is newer than both things the fingerprint carries but
 * no write to the world can announce (MCO-578):
 *
 *  - **the code** — its `revision` must be [DemandFingerprint.REVISION]. A deploy that changes the
 *    planner bumps it, and every world's plans re-derive as their roadmaps are opened.
 *  - **the game data** — it must have been derived after the world's version last finished
 *    ingesting. A re-ingest of the same version changes recipes under an unchanged version string.
 *
 * Both are read here rather than recomputing fingerprints because the fingerprint needs a project's
 * whole input set; these need one indexed join. Every other input is invalidated where it changes
 * (`DemandInvalidation.kt`).
 */
data class GetWorldDemandCoverageStep(val worldId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<Int>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<Int>> =
        DatabaseSteps.query<Unit, List<Int>>(
            sql = SafeSQL.select(
                """
                SELECT p.id
                FROM projects p
                JOIN world w ON w.id = p.world_id
                LEFT JOIN minecraft_version_ingestion i
                       ON i.version = w.version AND i.status = 'completed'
                WHERE p.world_id = ?
                  AND EXISTS (SELECT 1 FROM resource_gathering rg
                              WHERE rg.project_id = p.id AND rg.ignored = FALSE)
                  AND NOT EXISTS (SELECT 1 FROM project_demand_state s
                                  WHERE s.project_id = p.id
                                    AND s.revision = ?
                                    AND (i.completed_at IS NULL OR s.derived_at >= i.completed_at))
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, DemandFingerprint.REVISION)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) add(resultSet.getInt("id"))
                }
            },
        ).process(Unit)
}
