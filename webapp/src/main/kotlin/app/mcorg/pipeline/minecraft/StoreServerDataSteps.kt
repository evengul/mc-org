package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

data object StoreMinecraftDataStep : Step<ServerData, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: ServerData): Result<AppFailure.DatabaseError, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)

        return DatabaseSteps.transaction { connection ->
            object : Step<ServerData, AppFailure.DatabaseError, Unit> {
                override suspend fun process(input: ServerData): Result<AppFailure.DatabaseError, Unit> {
                    val versionResult = StoreMinecraftVersionStep(connection).process(input.version)

                    if (versionResult is Result.Failure) {
                        return versionResult
                    }

                    return StoreMinecraftItemDataStep(connection).process(input.version to input.items)
                } }
        }.process(input)
            .peek {
                logger.info("Successfully stored server data for version ${input.version}.")
            }
    }
}

private data class StoreMinecraftVersionStep(val connection: TransactionConnection) : Step<MinecraftVersion.Release, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: MinecraftVersion.Release): Result<AppFailure.DatabaseError, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        return DatabaseSteps.update<MinecraftVersion.Release>(
            sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"),
            parameterSetter = { statement, version ->
                statement.setString(1, version.toString())
            },
            connection
        ).process(input).map {
            logger.info("Stored minecraft version $input in the database.")
        }
    }
}

private data class StoreMinecraftItemDataStep(val connection: TransactionConnection) : Step<Pair<MinecraftVersion.Release, List<Item>>, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, List<Item>>): Result<AppFailure.DatabaseError, Unit> {
        val logger = LoggerFactory.getLogger(javaClass)
        return try {
            val (version, items) = input
            DatabaseSteps.batchUpdate<Item>(
                sql = SafeSQL.insert("""
                    INSERT INTO minecraft_items (version, item_id, item_name)
                    VALUES (?, ?, ?)
                    ON CONFLICT (version, item_id) DO NOTHING
                """.trimIndent()),
                parameterSetter = { statement, item ->
                    statement.setString(1, version.toString())
                    statement.setString(2, item.id)
                    statement.setString(3, item.name)
                },
                transactionConnection = connection
            ).process(input.second).map {
                logger.info("Stored ${items.size} items for minecraft version $version in the database.")
            }
        } catch (e: Exception) {
            logger.error("Error while storing minecraft item data for version ${input.first}", e)
            Result.failure(AppFailure.DatabaseError.UnknownError)
        }
    }
}