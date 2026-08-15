package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectDemand
import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.security.MessageDigest

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
         * Hashes every input [GenerateGatheringPlanStep] reads.
         *
         * Order is normalised so two runs over the same state agree. The world's farm supply is
         * in here as well as the project's own rows, which means marking any farm DONE
         * invalidates every other project's stored demand — correct, since that is exactly when
         * their chains stop expanding past the supplied item.
         */
        fun of(
            worldVersion: String,
            targets: List<Triple<String, Long, String?>>,
            supplied: Map<String, String>,
            overrides: List<Pair<String, String>>,
        ): DemandFingerprint {
            val payload = buildString {
                append("v1|").append(worldVersion).append('\n')
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
                            INSERT INTO project_demand_state (project_id, fingerprint, derived_at)
                            VALUES (?, ?, now())
                            ON CONFLICT (project_id)
                            DO UPDATE SET fingerprint = EXCLUDED.fingerprint, derived_at = EXCLUDED.derived_at
                            """.trimIndent()
                        ),
                        parameterSetter = { statement, _ ->
                            statement.setInt(1, projectId)
                            statement.setString(2, fingerprint.value)
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
 * Which projects in a world have no derived demand stored yet.
 *
 * The roadmap uses this to say so rather than quietly drawing a thinner graph — "3 projects have
 * not been planned yet" is a fact the user can act on, where a missing edge is one they cannot
 * see. Projects with nothing to gather at all are excluded: they are not waiting on a
 * derivation, they simply have no requirements.
 */
data class GetWorldDemandCoverageStep(val worldId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<Int>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<Int>> =
        DatabaseSteps.query<Unit, List<Int>>(
            sql = SafeSQL.select(
                """
                SELECT p.id
                FROM projects p
                WHERE p.world_id = ?
                  AND EXISTS (SELECT 1 FROM resource_gathering rg
                              WHERE rg.project_id = p.id AND rg.ignored = FALSE)
                  AND NOT EXISTS (SELECT 1 FROM project_demand_state s WHERE s.project_id = p.id)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) add(resultSet.getInt("id"))
                }
            },
        ).process(Unit)
}
