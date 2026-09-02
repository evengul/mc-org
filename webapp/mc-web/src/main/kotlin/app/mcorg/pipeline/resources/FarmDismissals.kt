package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * A farm-scale suggestion this world has decided against (MCO-407).
 *
 * [quantityAtDismissal] is display only, and the reason a dismissal can be permanent without
 * being a trap: the ignored list prints it beside today's demand, so an item that has grown
 * tenfold says so where the undo already is, instead of resurfacing on its own. See
 * `V2_63_0__create_world_farm_dismissals.sql` for why no re-suggestion rule exists.
 */
data class FarmDismissal(
    val itemId: String,
    val itemName: String,
    val quantityAtDismissal: Long,
)

/** What a world has dismissed, oldest first. */
object GetWorldFarmDismissalsStep : Step<Int, AppFailure, List<FarmDismissal>> {

    private val query = DatabaseSteps.query<Int, List<FarmDismissal>>(
        sql = SafeSQL.select(
            """
            SELECT item_id, item_name, quantity_at_dismissal
            FROM world_farm_dismissals
            WHERE world_id = ?
            ORDER BY created_at
            """.trimIndent()
        ),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        FarmDismissal(
                            itemId = rs.getString("item_id"),
                            itemName = rs.getString("item_name"),
                            quantityAtDismissal = rs.getLong("quantity_at_dismissal"),
                        )
                    )
                }
            }
        }
    )

    override suspend fun process(input: Int): Result<AppFailure, List<FarmDismissal>> = query.process(input)
}

/**
 * One dismissal to record. [itemName] and [quantity] are resolved from the plan by the handler,
 * never taken from the request — a dismissal is a decision about an item id, and the label is
 * only there so the undo list can name something no longer in any plan.
 */
data class DismissFarmDemandInput(
    val worldId: Int,
    val itemId: String,
    val itemName: String,
    val quantity: Long,
    val dismissedBy: Int,
)

/**
 * Records a dismissal, or refreshes the one already there.
 *
 * Upsert rather than insert: dismissing an item twice is the same decision, not two, and the
 * unique index says so. Re-dismissing after a restore updates the recorded quantity, which is
 * what you want — the decision was made against today's number, not the one from last month.
 */
object DismissFarmDemandStep : Step<DismissFarmDemandInput, AppFailure.DatabaseError, Int> {

    private val upsert = DatabaseSteps.update<DismissFarmDemandInput>(
        sql = SafeSQL.insert(
            """
            INSERT INTO world_farm_dismissals (world_id, item_id, item_name, quantity_at_dismissal, dismissed_by)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (world_id, item_id) DO UPDATE
                SET item_name = EXCLUDED.item_name,
                    quantity_at_dismissal = EXCLUDED.quantity_at_dismissal,
                    dismissed_by = EXCLUDED.dismissed_by,
                    created_at = CURRENT_TIMESTAMP
            RETURNING id
            """.trimIndent()
        ),
        parameterSetter = { ps, input ->
            ps.setInt(1, input.worldId)
            ps.setString(2, input.itemId)
            ps.setString(3, input.itemName)
            ps.setLong(4, input.quantity)
            ps.setInt(5, input.dismissedBy)
        }
    )

    override suspend fun process(input: DismissFarmDemandInput): Result<AppFailure.DatabaseError, Int> =
        upsert.process(input)
}

/** Which dismissal to take back. */
data class RestoreFarmDemandInput(val worldId: Int, val itemId: String)

/** Takes a dismissal back — the undo half of [DismissFarmDemandStep], and the whole of it. */
object RestoreFarmDemandStep : Step<RestoreFarmDemandInput, AppFailure.DatabaseError, Int> {

    private val delete = DatabaseSteps.update<RestoreFarmDemandInput>(
        sql = SafeSQL.delete("DELETE FROM world_farm_dismissals WHERE world_id = ? AND item_id = ?"),
        parameterSetter = { ps, input ->
            ps.setInt(1, input.worldId)
            ps.setString(2, input.itemId)
        }
    )

    override suspend fun process(input: RestoreFarmDemandInput): Result<AppFailure.DatabaseError, Int> =
        delete.process(input)
}

/**
 * What [worldId] has dismissed, or nothing at all if the read fails.
 *
 * Degrades rather than failing the page, the same posture as [GetFarmScaleThresholdStep] and
 * [farmSuggestionsFor]: a dismissal that briefly stops applying shows a line the user did not
 * want to see, which is a worse plan; a failed page is no plan.
 */
suspend fun farmDismissalsFor(worldId: Int): List<FarmDismissal> =
    GetWorldFarmDismissalsStep.process(worldId).getOrNull().orEmpty()

/** Just the ids, for the two rules that only need to know whether an item is out. */
fun List<FarmDismissal>.itemIds(): Set<String> = mapTo(mutableSetOf()) { it.itemId }
