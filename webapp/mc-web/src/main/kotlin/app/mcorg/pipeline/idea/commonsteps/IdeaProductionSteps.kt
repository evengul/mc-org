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

    val populated = modes.filter { it.rates.isNotEmpty() }
    if (populated.isEmpty()) return Result.success(Unit)

    populated.forEachIndexed { index, mode ->
        val modeIdResult = DatabaseSteps.query<Unit, Int?>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, ideaId)
                statement.setString(2, mode.name.ifBlank { IdeaProductionMode.DEFAULT_MODE_NAME })
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
