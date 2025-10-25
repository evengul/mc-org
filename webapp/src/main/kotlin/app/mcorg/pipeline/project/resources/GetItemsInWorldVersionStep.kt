package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

data object GetItemsInWorldVersionStep : Step<Int, DatabaseFailure, List<Item>> {
    override suspend fun process(input: Int): Result<DatabaseFailure, List<Item>> {
        return DatabaseSteps.query<Int, DatabaseFailure, List<Item>>(
            sql = SafeSQL.select("""
                SELECT item_id, item_name FROM minecraft_items JOIN world ON minecraft_items.version = world.version WHERE world.id = ?
            """.trimIndent()),
            parameterSetter = { statement, worldId -> statement.setInt(1, worldId) },
            errorMapper = { it },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val itemId = resultSet.getString("item_id")
                        val itemName = resultSet.getString("item_name")
                        add(Item(id = itemId, name = itemName))
                    }
                }
            }
        ).process(input)
    }
}