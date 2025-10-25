package app.mcorg.pipeline.minecraft

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import org.slf4j.LoggerFactory

data object StoreAllServerDataStep : Step<List<ServerData>, GetServerFilesFailure, Unit> {
    override suspend fun process(input: List<ServerData>): Result<GetServerFilesFailure, Unit> {
        for (serverData in input) {
            when (val result = StoreServerDataStep.process(serverData)) {
                is Result.Success -> continue
                is Result.Failure -> return Result.failure(result.error)
            }
        }
        return Result.success(Unit)
    }
}

data object StoreServerDataStep : Step<ServerData, GetServerFilesFailure, Unit> {
    override suspend fun process(input: ServerData): Result<GetServerFilesFailure, Unit> {
        val storeItemsResult = StoreMinecraftItemDataStep.process(Pair(input.version, input.items))
        return storeItemsResult
    }
}

data object StoreMinecraftItemDataStep : Step<Pair<MinecraftVersion.Release, List<Item>>, GetServerFilesFailure, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, List<Item>>): Result<GetServerFilesFailure, Unit> {
        return try {
            val (version, items) = input
            Database.getConnection().use { connection ->
                connection.prepareStatement("""
                    INSERT INTO minecraft_items (version, item_id, item_name)
                    VALUES (?, ?, ?)
                    ON CONFLICT (version, item_id) DO UPDATE SET item_name = EXCLUDED.item_name
                """.trimIndent()).use { statement ->
                    for (item in items) {
                        statement.setString(1, version.toString())
                        statement.setString(2, item.id)
                        statement.setString(3, item.name)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            LoggerFactory.getLogger(StoreMinecraftItemDataStep.javaClass).error("Error while storing minecraft item data for version ${input.first}", e)
            Result.failure(GetServerFilesFailure.DatabaseError)
        }
    }
}