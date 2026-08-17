package app.mcorg.pipeline.idea.commonsteps

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
)

/**
 * Replaces an idea's production modes wholesale.
 *
 * Delete-then-insert rather than a diff: modes are identified by name, and a rename is
 * indistinguishable from a delete plus an add without an id the form does not carry. The rows are
 * few (one mode for almost every idea, six for the worst case anyone has described) and always
 * written inside the caller's transaction, so the churn costs nothing and the alternative would be
 * a merge whose only purpose is preserving surrogate ids nothing references.
 *
 * Modes with no rates are dropped: a named way of running a farm that produces nothing is a form
 * artefact, not a fact about the farm.
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

    val populated = modes.filter { it.rates.isNotEmpty() }.mergeByResolvedName()
    if (populated.isEmpty()) return Result.success(Unit)

    populated.forEachIndexed { index, mode ->
        val modeIdResult = DatabaseSteps.query<Unit, Int?>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, ideaId)
                // Already resolved by mergeByResolvedName — blank became DEFAULT_MODE_NAME there.
                statement.setString(2, mode.name)
                statement.setInt(3, index)
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
            existing.copy(rates = rates)
        }
        acc
    }.values.toList()

/** An idea's production modes, in the author's order, each with its rates. */
data class GetIdeaProductionModesStep(val ideaId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<IdeaProductionMode>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<IdeaProductionMode>> =
        DatabaseSteps.query<Unit, List<IdeaProductionMode>>(
            sql = SafeSQL.select(
                """
                SELECT m.id, m.name, m.position, r.item_id, r.rate_per_hour
                FROM idea_production_modes m
                LEFT JOIN idea_production_rates r ON r.mode_id = m.id
                WHERE m.idea_id = ?
                ORDER BY m.position, m.id, r.item_id
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, ideaId) },
            resultMapper = { rs ->
                // LEFT JOIN so a mode with no rates still arrives; the row then carries a null
                // item and contributes nothing but the mode itself.
                val byId = linkedMapOf<Int, IdeaProductionMode>()
                while (rs.next()) {
                    val id = rs.getInt("id")
                    val mode = byId.getOrPut(id) {
                        IdeaProductionMode(
                            id = id,
                            name = rs.getString("name"),
                            position = rs.getInt("position"),
                            rates = emptyMap(),
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
        ).process(Unit)
}
