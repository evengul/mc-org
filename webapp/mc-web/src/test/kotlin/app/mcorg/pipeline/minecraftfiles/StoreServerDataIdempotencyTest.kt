package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Re-ingesting a version must replace its source data, not duplicate it (resource_source has no
 * natural unique key and its insert is not idempotent). Guards the MCO-168 delete-before-insert.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class StoreServerDataIdempotencyTest {

    private val version = MinecraftVersion.Release(1, 99, 0)

    private val data = ServerData(
        version = version,
        items = listOf(Item("minecraft:stone", "Stone")),
        sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "stone.json",
                producedItems = listOf(Item("minecraft:stone", "Stone") to ResourceQuantity.ItemQuantity(1)),
            )
        ),
    )

    @BeforeEach
    fun clean() {
        // Reverse dependency order; resource_source children cascade with their parent.
        DatabaseTestExtension.executeSQL("DELETE FROM resource_source WHERE version = '$version'")
        DatabaseTestExtension.executeSQL("DELETE FROM minecraft_items WHERE version = '$version'")
        DatabaseTestExtension.executeSQL("DELETE FROM minecraft_version WHERE version = '$version'")
    }

    @Test
    fun `re-ingesting a version replaces its sources instead of duplicating them`() {
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }
        val sourcesAfterFirst = count("resource_source")
        val producedAfterFirst = count("resource_source_produced_item")
        assertEquals(1, sourcesAfterFirst)
        assertEquals(1, producedAfterFirst)

        // Second ingest of the identical data (the post-MCO-168 NULL-SHA / SHA-change re-ingest path).
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }

        assertEquals(1, count("resource_source"), "resource_source duplicated on re-ingest")
        assertEquals(1, count("resource_source_produced_item"), "produced rows duplicated on re-ingest")
    }

    private fun count(table: String): Int = runBlocking {
        DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT count(*) AS c FROM $table WHERE version = ?"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs -> if (rs.next()) rs.getInt("c") else 0 }
        ).process(Unit).getOrThrow()
    }
}
