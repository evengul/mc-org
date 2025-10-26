package app.mcorg.pipeline.minecraft

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import org.slf4j.LoggerFactory

data object StoreMinecraftDataStep : Step<ServerData, GetServerFilesFailure, Unit> {
    override suspend fun process(input: ServerData): Result<GetServerFilesFailure, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        val serverDataPipeline = Pipeline.create<GetServerFilesFailure, Unit>()
            .map { input.version }
            .pipe(StoreMinecraftVersionStep)
            .map { input.version to input.items }
            .pipe(StoreMinecraftItemDataStep)

        return serverDataPipeline.execute(Unit)
            .peek {
                logger.info("Successfully stored server data for version ${input.version}.")
            }
    }
}

data object StoreMinecraftVersionStep : Step<MinecraftVersion.Release, GetServerFilesFailure, Unit> {
    override suspend fun process(input: MinecraftVersion.Release): Result<GetServerFilesFailure, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        return DatabaseSteps.update<MinecraftVersion.Release, GetServerFilesFailure>(
            sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"),
            parameterSetter = { statement, version ->
                statement.setString(1, version.toString())
            },
            errorMapper = { GetServerFilesFailure.DatabaseError(this.javaClass) }
        ).process(input).map {
            logger.info("Stored minecraft version $input in the database.")
        }
    }
}

data object StoreMinecraftItemDataStep : Step<Pair<MinecraftVersion.Release, List<Item>>, GetServerFilesFailure, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, List<Item>>): Result<GetServerFilesFailure, Unit> {
        val logger = LoggerFactory.getLogger(javaClass)
        return try {
            val (version, items) = input
            Database.getConnection().use { connection ->
                connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM minecraft_items WHERE version = ?)").use { checkStatement ->
                    checkStatement.setString(1, version.toString())
                    checkStatement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val exists = resultSet.getBoolean(1)
                            if (exists) {
                                logger.info("Item data for version $version already exists in the database. Skipping insertion.")
                                return Result.success(Unit)
                            }
                        }
                    }
                }

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
                    logger.info("Stored ${items.size} items for version $version in the database.")
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            logger.error("Error while storing minecraft item data for version ${input.first}", e)
            Result.failure(GetServerFilesFailure.DatabaseError(this.javaClass))
        }
    }
}