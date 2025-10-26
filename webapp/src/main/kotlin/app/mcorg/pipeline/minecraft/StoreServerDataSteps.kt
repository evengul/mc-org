package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import org.slf4j.LoggerFactory

data object StoreMinecraftDataStep : Step<ServerData, GetServerFilesFailure, Unit> {
    override suspend fun process(input: ServerData): Result<GetServerFilesFailure, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)

        return DatabaseSteps.transaction(object : Step<ServerData, GetServerFilesFailure, Unit> {
            override suspend fun process(input: ServerData): Result<GetServerFilesFailure, Unit> {
                val versionResult = StoreMinecraftVersionStep.process(input.version)

                if (versionResult is Result.Failure) {
                    return Result.failure(versionResult.error)
                }

                return StoreMinecraftItemDataStep.process(input.version to input.items)
            } },
            errorMapper = { GetServerFilesFailure.DatabaseError(this.javaClass) }).process(input)
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
            DatabaseSteps.batchUpdate<Item, GetServerFilesFailure>(
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
                errorMapper = { GetServerFilesFailure.DatabaseError(this.javaClass) }
            ).process(input.second).map {
                logger.info("Stored ${items.size} items for minecraft version $version in the database.")
            }
        } catch (e: Exception) {
            logger.error("Error while storing minecraft item data for version ${input.first}", e)
            Result.failure(GetServerFilesFailure.DatabaseError(this.javaClass))
        }
    }
}