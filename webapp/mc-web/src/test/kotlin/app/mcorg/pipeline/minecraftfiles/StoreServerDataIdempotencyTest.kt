package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
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
        DatabaseTestExtension.executeSQL("DELETE FROM minecraft_tag WHERE version = '$version'")
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

    /**
     * The item insert is `ON CONFLICT DO NOTHING`, so without an explicit delete the catalog is
     * append-only and no extraction change that *removes* an item can ever take effect — the
     * pruned id sits in minecraft_items forever, permanently BLOCKED. This is what made the
     * MCO-313 lang-key fix inert until storage was corrected.
     */
    @Test
    fun `re-ingesting retires items the version no longer has`() {
        val withLegacyId = data.copy(
            items = data.items + Item("minecraft:chain", "Chain"),
        )
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(withLegacyId)) }
        assertEquals(2, count("minecraft_items"))

        // Extraction now prunes the legacy id; the stored catalog must follow.
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }

        assertEquals(1, count("minecraft_items"), "stale item survived re-ingest")
        assertEquals(
            listOf("minecraft:stone"),
            itemIds(),
            "the retired id must be the one extraction dropped"
        )
    }

    @Test
    fun `re-ingesting keeps items the version still has`() {
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }

        assertEquals(listOf("minecraft:stone"), itemIds())
        assertEquals(1, count("resource_source"), "retirement must not cascade live sources away")
    }

    /**
     * `item_id <> ALL ('{}')` is TRUE for every row, so an extraction that yields nothing would
     * delete the version's entire catalog — and cascade its tags and sources away with it —
     * silently, inside the ingest transaction. A version with no items is always an extraction
     * fault, never a real registry, so the retirement is skipped rather than obeyed.
     */
    @Test
    fun `an empty extraction does not wipe the catalog`() {
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data)) }
        assertEquals(listOf("minecraft:stone"), itemIds())

        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(data.copy(items = emptyList()))) }

        assertEquals(listOf("minecraft:stone"), itemIds(), "an empty extraction wiped the catalog")
        assertEquals(1, count("resource_source"), "the wipe cascaded the version's sources away")
    }

    /**
     * A tag's name is extraction output too, so an extraction change that renames one has to be
     * able to land. `ON CONFLICT DO NOTHING` meant it could not: MCO-489 renamed every tag and
     * bumped ExtractionVersion to force the re-ingest, which then wrote nothing at all because
     * the rows already existed.
     */
    @Test
    fun `re-ingesting updates a tag whose name extraction changed`() {
        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(withTag("Smelts To Glass"))) }
        assertEquals(listOf("Smelts To Glass"), tagNames())

        runBlocking { assertIs<Result.Success<*>>(StoreMinecraftDataStep.process(withTag("Red Sand or Sand"))) }

        assertEquals(listOf("Red Sand or Sand"), tagNames(), "the renamed tag did not survive re-ingest")
        assertEquals(1, count("minecraft_tag"), "the rename inserted a second row instead of updating")
    }

    /**
     * ExtractMinecraftDataStep lifts every id a source references into `items`, tags included —
     * that list is what the store step reads, so the tag goes there as well as in the source.
     */
    private fun withTag(tagName: String): ServerData {
        val tag = MinecraftTag(
            "#minecraft:smelts_to_glass",
            tagName,
            listOf(Item("minecraft:sand", "Sand (Block)")),
        )
        return data.copy(
            items = data.items + tag,
            sources = data.sources + ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.SMELTING,
                filename = "glass.json",
                producedItems = listOf(Item("minecraft:stone", "Stone") to ResourceQuantity.ItemQuantity(1)),
                requiredItems = listOf(tag to ResourceQuantity.ItemQuantity(1)),
            ),
        )
    }

    private fun tagNames(): List<String> = runBlocking {
        DatabaseSteps.query<Unit, List<String>>(
            sql = SafeSQL.select("SELECT name FROM minecraft_tag WHERE version = ? ORDER BY tag"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.getString("name")) } }
        ).process(Unit).getOrThrow()
    }

    private fun itemIds(): List<String> = runBlocking {
        DatabaseSteps.query<Unit, List<String>>(
            sql = SafeSQL.select("SELECT item_id FROM minecraft_items WHERE version = ? ORDER BY item_id"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.getString("item_id")) } }
        ).process(Unit).getOrThrow()
    }

    private fun count(table: String): Int = runBlocking {
        DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT count(*) AS c FROM $table WHERE version = ?"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs -> if (rs.next()) rs.getInt("c") else 0 }
        ).process(Unit).getOrThrow()
    }
}
