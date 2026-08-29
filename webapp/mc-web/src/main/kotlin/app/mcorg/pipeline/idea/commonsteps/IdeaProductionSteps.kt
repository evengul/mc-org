package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.TransactionConnection

/**
 * A mode as submitted, before it has an id.
 *
 * [name] is blank for the implicit single mode — the form never asks for one until a second mode
 * exists, so blank means "the only way this runs" and is stored as
 * [IdeaProductionMode.DEFAULT_MODE_NAME].
 */
data class IdeaProductionModeInput(
    val name: String,
    /** Item id -> rate, or null where the author knows what it makes but not how fast. */
    val rates: Map<String, Int?>,
    val kind: IdeaModeKind = IdeaModeKind.Default,
    /**
     * What this variant costs to build, item id -> quantity. Meaningful only for
     * [IdeaModeKind.BUILD_TIME] modes; a runtime mode does not change what the build cost, so a
     * non-empty map here on a runtime mode is a caller error and is dropped rather than stored
     * (see [requirementsToStore]).
     */
    val requirements: Map<String, Int> = emptyMap(),
) {
    /**
     * The requirements this mode should actually own, which is none unless it is build-time.
     *
     * Enforced here rather than by a database constraint: a CHECK cannot see across to
     * `idea_production_modes.kind`, and the invariant has exactly two write paths, both of which
     * come through this file.
     */
    val requirementsToStore: Map<String, Int>
        get() = if (kind == IdeaModeKind.BUILD_TIME) requirements else emptyMap()
}

/**
 * Whether these modes carry their own material lists, and so *replace* the idea's base list.
 *
 * The base list (`mode_id IS NULL`) and build-time mode lists are alternatives, never both: an
 * idea either has one list, or one per build-time variant. Write paths ask this before inserting a
 * base list, so an idea that describes its variants does not also keep a list belonging to none of
 * them.
 */
fun List<IdeaProductionModeInput>.replaceBaseRequirements(): Boolean =
    any { it.requirementsToStore.isNotEmpty() }

/**
 * Replaces an idea's production modes wholesale.
 *
 * Delete-then-insert rather than a diff: modes are identified by name, and a rename is
 * indistinguishable from a delete plus an add without an id the form does not carry. The rows are
 * few (one mode for almost every idea, six for the worst case anyone has described) and always
 * written inside the caller's transaction, so the churn costs nothing and the alternative would be
 * a merge whose only purpose is preserving surrogate ids nothing references.
 *
 * Modes with nothing in them are dropped: a named way of running a farm that produces nothing is a
 * form artefact, not a fact about the farm. Since MCO-463 "nothing" means neither rates *nor*
 * requirements — a build-time variant whose output nobody timed is still a real variant, because
 * it still says what building it costs.
 *
 * Modes that resolve to the *same* name are merged rather than inserted twice, which would violate
 * `UNIQUE (idea_id, name)` and roll the whole publish back. The form rejects this case with a
 * field-level message (`ValidateIdeaProductionsStep`), but a bodyless publish re-publishes stored
 * draft JSON without re-validating it, so the collision has to be survivable here too. Merging is
 * the non-lossy reading: two modes claiming the same name claim to be the same way of running the
 * farm, so their items combine, and the faster of two rates for one item wins — consistent with
 * [app.mcorg.domain.model.idea.bestRateFor], which already answers "how fast, at best".
 */
suspend fun replaceIdeaProductionModes(
    ideaId: Int,
    modes: List<IdeaProductionModeInput>,
    connection: TransactionConnection,
): Result<AppFailure.DatabaseError, Unit> {
    val cleared = DatabaseSteps.update<Int>(
        SafeSQL.delete("DELETE FROM idea_production_modes WHERE idea_id = ?"),
        parameterSetter = { statement, id -> statement.setInt(1, id) },
        transactionConnection = connection,
    ).process(ideaId)
    if (cleared is Result.Failure) return cleared

    val populated = modes
        .filter { it.rates.isNotEmpty() || it.requirementsToStore.isNotEmpty() }
        .mergeByResolvedName()
    if (populated.isEmpty()) return Result.success(Unit)

    populated.forEachIndexed { index, mode ->
        val modeIdResult = DatabaseSteps.query<Unit, Int?>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position, kind) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, ideaId)
                // Already resolved by mergeByResolvedName — blank became DEFAULT_MODE_NAME there.
                statement.setString(2, mode.name)
                statement.setInt(3, index)
                statement.setString(4, mode.kind.name)
            },
            resultMapper = { rs -> if (rs.next()) rs.getInt("id") else null },
            transactionConnection = connection,
        ).process(Unit)

        val modeId = when (modeIdResult) {
            is Result.Success -> modeIdResult.value ?: return Result.failure(AppFailure.DatabaseError.NotFound)
            is Result.Failure -> return modeIdResult
        }

        val rates = DatabaseSteps.batchUpdate<Pair<String, Int?>>(
            SafeSQL.insert(
                "INSERT INTO idea_production_rates (mode_id, item_id, rate_per_hour) VALUES (?, ?, ?)"
            ),
            parameterSetter = { statement, (itemId, rate) ->
                statement.setInt(1, modeId)
                statement.setString(2, itemId)
                if (rate == null) statement.setNull(3, java.sql.Types.INTEGER) else statement.setInt(3, rate)
            },
            transactionConnection = connection,
        ).process(mode.rates.toList())
        if (rates is Result.Failure) return rates

        // A build-time mode's own material list, replacing the idea's base list rather than adding
        // to it (V2_61_0). Runtime modes never reach here — requirementsToStore is empty for them.
        val requirements = mode.requirementsToStore
        if (requirements.isNotEmpty()) {
            val stored = DatabaseSteps.batchUpdate<Pair<String, Int>>(
                SafeSQL.insert(
                    "INSERT INTO idea_item_requirements (idea_id, item_id, quantity, mode_id) VALUES (?, ?, ?, ?)"
                ),
                parameterSetter = { statement, (itemId, quantity) ->
                    statement.setInt(1, ideaId)
                    statement.setString(2, itemId)
                    statement.setInt(3, quantity)
                    statement.setInt(4, modeId)
                },
                chunkSize = 500,
                transactionConnection = connection,
            ).process(requirements.toList())
            if (stored is Result.Failure) return stored
        }
    }

    return Result.success(Unit)
}

/**
 * Collapses modes sharing a resolved name into one, preserving first-seen order.
 *
 * Blank resolves to [IdeaProductionMode.DEFAULT_MODE_NAME] first, so two unnamed modes are one
 * collision rather than two rows the unique index would reject. Where both carry the same item, the
 * higher rate wins and a measured rate beats an unmeasured one — a null here means "never timed",
 * not "zero", so it must never displace a real number.
 *
 * Two further fields merge since MCO-463, on the same "take the stronger claim" reading:
 *
 *  - **kind** — [IdeaModeKind.BUILD_TIME] wins. It is the claim that carries consequences (its own
 *    material list, a choice forced at import), and silently demoting it to runtime would drop
 *    requirements on the floor at the next write.
 *  - **requirements** — the larger quantity wins, per item. Same argument as rates: two modes
 *    claiming one name claim to be one variant, and under-stating what it costs to build is the
 *    more expensive way to be wrong.
 */
internal fun List<IdeaProductionModeInput>.mergeByResolvedName(): List<IdeaProductionModeInput> =
    fold(LinkedHashMap<String, IdeaProductionModeInput>()) { acc, mode ->
        val name = mode.name.trim().ifBlank { IdeaProductionMode.DEFAULT_MODE_NAME }
        val existing = acc[name]
        acc[name] = if (existing == null) {
            mode.copy(name = name)
        } else {
            val rates = existing.rates.toMutableMap()
            mode.rates.forEach { (itemId, rate) ->
                val current = rates[itemId]
                rates[itemId] = when {
                    !rates.containsKey(itemId) -> rate
                    current == null -> rate
                    rate == null -> current
                    else -> maxOf(current, rate)
                }
            }
            val requirements = existing.requirements.toMutableMap()
            mode.requirements.forEach { (itemId, quantity) ->
                requirements[itemId] = maxOf(requirements[itemId] ?: 0, quantity)
            }
            val kind = if (existing.kind == IdeaModeKind.BUILD_TIME || mode.kind == IdeaModeKind.BUILD_TIME) {
                IdeaModeKind.BUILD_TIME
            } else {
                existing.kind
            }
            existing.copy(rates = rates, requirements = requirements, kind = kind)
        }
        acc
    }.values.toList()

/**
 * An idea's production modes, in the author's order, each with its rates, kind and — for build-time
 * modes — its own material list.
 *
 * Two queries rather than one. A second `LEFT JOIN` onto `idea_item_requirements` would multiply
 * rows (every rate against every requirement: five rates and three hundred materials is fifteen
 * hundred rows to rebuild two maps from). The maps would still come out right, since duplicates
 * collapse on insert, but paying a cartesian product to avoid a round trip on a handful of rows is
 * the wrong trade — and the join that reads correct but computes nonsense is exactly the kind of
 * thing nobody revisits later.
 */
data class GetIdeaProductionModesStep(val ideaId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<IdeaProductionMode>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<IdeaProductionMode>> {
        val ratesResult = ratesQuery.process(Unit)
        val modes = when (ratesResult) {
            is Result.Success -> ratesResult.value
            is Result.Failure -> return ratesResult
        }
        // Only build-time modes can own a material list, so an idea with none — every idea in the
        // bank before MCO-463 — costs exactly the one query it always did.
        if (modes.none { it.isBuildTime }) return Result.success(modes)

        val requirementsResult = requirementsQuery.process(Unit)
        val requirements = when (requirementsResult) {
            is Result.Success -> requirementsResult.value
            is Result.Failure -> return requirementsResult
        }
        return Result.success(
            modes.map { mode ->
                val own = requirements[mode.id]
                if (own == null) mode else mode.copy(requirements = own)
            }
        )
    }

    private val ratesQuery = DatabaseSteps.query<Unit, List<IdeaProductionMode>>(
        sql = SafeSQL.select(
            """
            SELECT m.id, m.name, m.position, m.kind, r.item_id, r.rate_per_hour
            FROM idea_production_modes m
            LEFT JOIN idea_production_rates r ON r.mode_id = m.id
            WHERE m.idea_id = ?
            ORDER BY m.position, m.id, r.item_id
            """.trimIndent()
        ),
        parameterSetter = { statement, _ -> statement.setInt(1, ideaId) },
        resultMapper = { rs ->
            // LEFT JOIN so a mode with no rates still arrives; the row then carries a null
            // item and contributes nothing but the mode itself. Since MCO-463 that is a real
            // case rather than a degenerate one — a build-time variant can state what it costs
            // without anyone having timed what it makes.
            val byId = linkedMapOf<Int, IdeaProductionMode>()
            while (rs.next()) {
                val id = rs.getInt("id")
                val mode = byId.getOrPut(id) {
                    IdeaProductionMode(
                        id = id,
                        name = rs.getString("name"),
                        position = rs.getInt("position"),
                        rates = emptyMap(),
                        kind = IdeaModeKind.fromOrDefault(rs.getString("kind")),
                    )
                }
                val itemId = rs.getString("item_id")
                if (itemId != null) {
                    // getInt returns 0 for SQL NULL, and 0 is not what a missing rate means —
                    // wasNull is the only way to tell "unmeasured" from a real zero.
                    val rate = rs.getInt("rate_per_hour").takeUnless { rs.wasNull() }
                    byId[id] = mode.copy(rates = mode.rates + (itemId to rate))
                }
            }
            byId.values.toList()
        }
    )

    /** Mode id -> that build-time mode's material list. */
    private val requirementsQuery = DatabaseSteps.query<Unit, Map<Int, Map<String, Int>>>(
        sql = SafeSQL.select(
            """
            SELECT r.mode_id, r.item_id, r.quantity
            FROM idea_item_requirements r
            JOIN idea_production_modes m ON m.id = r.mode_id
            WHERE m.idea_id = ?
            ORDER BY r.quantity DESC
            """.trimIndent()
        ),
        parameterSetter = { statement, _ -> statement.setInt(1, ideaId) },
        resultMapper = { rs ->
            val byMode = mutableMapOf<Int, MutableMap<String, Int>>()
            while (rs.next()) {
                byMode.getOrPut(rs.getInt("mode_id")) { linkedMapOf() }[rs.getString("item_id")] =
                    rs.getInt("quantity")
            }
            byMode
        }
    )
}
