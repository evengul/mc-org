package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data object StoreMinecraftDataStep : Step<ServerData, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: ServerData): Result<AppFailure.DatabaseError, Unit> {
        val logger = LoggerFactory.getLogger(this.javaClass)

        return DatabaseSteps.transaction { connection ->
            object : Step<ServerData, AppFailure.DatabaseError, Unit> {
                override suspend fun process(input: ServerData): Result<AppFailure.DatabaseError, Unit> {
                    StoreMinecraftVersionStep(connection).process(input.version)

                    StoreMinecraftItemDataStep(connection).process(input.version to input.items)

                    StoreResourceSourcesStep(connection).process(input.version to input.sources)

                    return Result.success()
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

private data class StoreMinecraftItemDataStep(val connection: TransactionConnection) : Step<Pair<MinecraftVersion.Release, List<MinecraftId>>, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, List<MinecraftId>>): Result<AppFailure.DatabaseError, Unit> {
        val logger = LoggerFactory.getLogger(javaClass)
        return try {
            val (version, itemsAndTags) = input

            val tags = itemsAndTags.filterIsInstance<MinecraftTag>().distinctBy { it.id }
            val tagItems = tags.flatMap { it.content }
            val allItems = (itemsAndTags.filterIsInstance<Item>() + tagItems).distinctBy { it.id }

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
            ).process(allItems).map {
                logger.info("Stored ${itemsAndTags.size} items for minecraft version $version in the database.")
            }

            DatabaseSteps.batchUpdate<MinecraftTag>(
                sql = SafeSQL.insert("""
                    INSERT INTO minecraft_tag (version, tag, name) 
                    VALUES (?, ?, ?)
                    ON CONFLICT (version, tag) DO NOTHING
                """.trimIndent()),
                parameterSetter = { statement, tag ->
                    statement.setString(1, version.toString())
                    statement.setString(2, tag.id)
                    statement.setString(3, tag.name)
                }
            ).process(tags).map {
                logger.info("Stored ${tags.size} tags for minecraft version $version in the database.")
            }

            DatabaseSteps.batchUpdate<Pair<MinecraftTag, Item>>(
                sql = SafeSQL.insert("""
                    INSERT INTO minecraft_tag_item (version, tag, item) 
                    VALUES (?, ?, ?)
                    ON CONFLICT (version, tag, item) DO NOTHING
                """.trimIndent()),
                parameterSetter = { statement, (tag, item) ->
                    statement.setString(1, version.toString())
                    statement.setString(2, tag.id)
                    statement.setString(3, item.id)
                },
            ).process(tags.flatMap { tag -> tag.content.map { item -> tag to item } }).map {
                logger.info("Stored tag-item relationships for minecraft version $version in the database.")
            }
        } catch (e: Exception) {
            logger.error("Error while storing minecraft item data for version ${input.first}", e)
            Result.failure(AppFailure.DatabaseError.UnknownError)
        }
    }
}

private data class StoreResourceSourcesStep(val connection: TransactionConnection) : Step<Pair<MinecraftVersion.Release, List<ResourceSource>>, AppFailure, Unit> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, List<ResourceSource>>): Result<AppFailure, Unit> {
        return try {
            val (version, sources) = input
            DatabaseSteps.batchUpdate<ResourceSource>(
                sql = SafeSQL.with("""
                    WITH inserted_source AS (
                        INSERT INTO resource_source (version, source_type, created_from_filename)
                        VALUES (?, ?, ?)
                        RETURNING id, version
                    ),
                    produced_items AS (
                        INSERT INTO resource_source_produced_item (version, resource_source_id, item, count)
                        SELECT inserted_source.version, inserted_source.id, 
                               unnest(?::text[]), unnest(?::int[])
                        FROM inserted_source
                        WHERE array_length(?::text[], 1) > 0
                        RETURNING id
                    ),
                    produced_tags AS (
                        INSERT INTO resource_source_produced_tag (version, resource_source_id, tag, count)
                        SELECT inserted_source.version, inserted_source.id, 
                               unnest(?::text[]), unnest(?::int[])
                        FROM inserted_source
                        WHERE array_length(?::text[], 1) > 0
                        RETURNING id
                    ),
                    consumed_items AS (
                        INSERT INTO resource_source_consumed_item (version, resource_source_id, item, count)
                        SELECT inserted_source.version, inserted_source.id, 
                               unnest(?::text[]), unnest(?::int[])
                        FROM inserted_source
                        WHERE array_length(?::text[], 1) > 0
                        RETURNING id
                    ),
                    consumed_tags AS (
                        INSERT INTO resource_source_consumed_tag (version, resource_source_id, tag, count)
                        SELECT inserted_source.version, inserted_source.id, 
                               unnest(?::text[]), unnest(?::int[])
                        FROM inserted_source
                        WHERE array_length(?::text[], 1) > 0
                        RETURNING id
                    )
                    SELECT id FROM inserted_source
                """.trimIndent()),
                parameterSetter = { statement, source ->
                    val producedItems = source.producedItems.filterIsInstance<Item>()
                    val producedTags = source.producedItems.filterIsInstance<MinecraftTag>()
                    val consumedItems = source.requiredItems.filterIsInstance<Item>()
                    val consumedTags = source.requiredItems.filterIsInstance<MinecraftTag>()

                    // Insert source (3 parameters)
                    statement.setString(1, version.toString())
                    statement.setString(2, source.type.id)
                    statement.setString(3, source.filename)

                    // Produced items (3 parameters: items array, counts array, check array)
                    statement.setArray(4, connection.connection.createArrayOf("text", producedItems.map { it.id }.toTypedArray()))
                    statement.setArray(5, connection.connection.createArrayOf("integer", Array(producedItems.size) { 1 /*TODO: Change to actual value when available*/ }))
                    statement.setArray(6, connection.connection.createArrayOf("text", producedItems.map { it.id }.toTypedArray()))

                    // Produced tags (3 parameters)
                    statement.setArray(7, connection.connection.createArrayOf("text", producedTags.map { it.id }.toTypedArray()))
                    statement.setArray(8, connection.connection.createArrayOf("integer", Array(producedTags.size) { 1 }))
                    statement.setArray(9, connection.connection.createArrayOf("text", producedTags.map { it.id }.toTypedArray()))

                    // Consumed items (3 parameters)
                    statement.setArray(10, connection.connection.createArrayOf("text", consumedItems.map { it.id }.toTypedArray()))
                    statement.setArray(11, connection.connection.createArrayOf("integer", Array(consumedItems.size) { 1 }))
                    statement.setArray(12, connection.connection.createArrayOf("text", consumedItems.map { it.id }.toTypedArray()))

                    // Consumed tags (3 parameters)
                    statement.setArray(13, connection.connection.createArrayOf("text", consumedTags.map { it.id }.toTypedArray()))
                    statement.setArray(14, connection.connection.createArrayOf("integer", Array(consumedTags.size) { 1 }))
                    statement.setArray(15, connection.connection.createArrayOf("text", consumedTags.map { it.id }.toTypedArray()))
                },
                transactionConnection = connection
            ).process(sources).map {
                val logger = LoggerFactory.getLogger(javaClass)
                logger.info("Stored ${sources.size} resource sources for minecraft version $version in the database.")
            }
        } catch (e: Exception) {
            val logger = LoggerFactory.getLogger(javaClass)
            logger.error("Error while storing resource sources for version ${input.first}", e)
            Result.failure(AppFailure.DatabaseError.UnknownError)
        }
    }
}

private data class StoreMinecraftItemSourceGraphStep(val connection: TransactionConnection) : Step<Pair<MinecraftVersion.Release, ItemSourceGraph>, AppFailure.DatabaseError, Unit> {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val serializer = Json {
        allowStructuredMapKeys = true
    }

    override suspend fun process(input: Pair<MinecraftVersion.Release, ItemSourceGraph>): Result<AppFailure.DatabaseError, Unit> {
        val (version, graph) = input
        return try {
            DatabaseSteps.update<ItemSourceGraph>(
                sql = SafeSQL.insert("""
                INSERT INTO item_graph (minecraft_version, graph_data)
                VALUES (?, ?::jsonb)
                ON CONFLICT (minecraft_version) DO UPDATE SET graph_data = excluded.graph_data
            """.trimIndent()),
                parameterSetter = { statement, graph ->
                    statement.setString(1, version.toString())
                    statement.setString(2, serializer.encodeToString(graph))
                },
                transactionConnection = connection
            ).process(graph).map {
                logger.info("Stored item source graph for minecraft version $version in the database.")
            }
        } catch (e: Exception) {
            logger.error("Error while storing item source graph for version $version", e)
            Result.failure(AppFailure.DatabaseError.UnknownError)
        }
    }
}