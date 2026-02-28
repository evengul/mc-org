package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val GetItemsInWorldVersionStep = DatabaseSteps.query<Int, List<Item>>(
    sql = SafeSQL.select("""
                SELECT item_id, item_name FROM minecraft_items JOIN world ON minecraft_items.version = world.version WHERE world.id = ?
            """.trimIndent()),
    parameterSetter = { statement, worldId -> statement.setInt(1, worldId) },
    resultMapper = { resultSet ->
        buildList {
            while (resultSet.next()) {
                val itemId = resultSet.getString("item_id")
                val itemName = resultSet.getString("item_name")
                add(Item(id = itemId, name = itemName))
            }
        }
    }
)