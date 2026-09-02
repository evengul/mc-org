package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/** (worldId, itemId) — the catalog is per Minecraft version, and a world pins the version. */
data class ItemNameInput(val worldId: Int, val itemId: String)

/**
 * One item's display name from the catalog, e.g. `minecraft:snow_block` -> "Snow Block (Block)".
 *
 * Exists for the row a finished line leaves behind. `GenerateGatheringPlanStep` drops
 * fully-collected targets, so the moment you tick the last one the plan no longer carries its
 * name — and the id-derived fallback renders "Snow block", losing both the capital and the
 * "(Block)" that tells it apart from the item. The client knows the right name, but taking
 * display text from the client to echo back is the wrong shape; the catalog already has it.
 */
object GetItemNameStep : Step<ItemNameInput, AppFailure, String?> {

    private val query = DatabaseSteps.query<ItemNameInput, String?>(
        sql = SafeSQL.select(
            """
            SELECT mi.item_name
            FROM minecraft_items mi
            JOIN world w ON mi.version = w.version
            WHERE w.id = ? AND mi.item_id = ?
            """.trimIndent()
        ),
        parameterSetter = { statement, input ->
            statement.setInt(1, input.worldId)
            statement.setString(2, input.itemId)
        },
        resultMapper = { rs -> if (rs.next()) rs.getString("item_name") else null }
    )

    override suspend fun process(input: ItemNameInput): Result<AppFailure, String?> = query.process(input)
}
