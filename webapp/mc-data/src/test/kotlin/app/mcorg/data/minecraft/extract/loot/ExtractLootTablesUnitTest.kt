package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.data.minecraft.extract.ExtractionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unit-level coverage for [ExtractLootTables] that doesn't require downloaded server data
 * (see [ExtractLootTablesTest] for the real-data E2E coverage) — specifically the
 * infested-block phantom-loot guard from MCO-248.
 */
class ExtractLootTablesUnitTest {

    private val version = MinecraftVersion.Release(1, 21, 0)

    @TempDir
    lateinit var tempDir: Path

    private fun contextFor(tempDir: Path): ExtractionContext = ExtractionContext(
        version = version,
        root = tempDir,
        names = mapOf(
            "minecraft:stone_bricks" to "Stone Bricks (Item)",
            "minecraft:infested_stone_bricks" to "Infested Stone Bricks (Item)",
        ),
        tags = emptyMap(),
        itemIds = setOf("minecraft:stone_bricks", "minecraft:infested_stone_bricks"),
    )

    private fun writeLootTable(tempDir: Path, relativePath: String, json: String) {
        val file = tempDir.resolve("loot_table").resolve(relativePath)
        file.parent.toFile().mkdirs()
        file.toFile().writeText(json)
    }

    @Test
    fun `isPhantomInfestedBlockLoot matches infested block filenames`() {
        assertTrue(isPhantomInfestedBlockLoot("blocks/infested_stone_bricks.json"))
        assertTrue(isPhantomInfestedBlockLoot("infested_deepslate.json"))
        assertFalse(isPhantomInfestedBlockLoot("blocks/stone_bricks.json"))
        assertFalse(isPhantomInfestedBlockLoot("blocks/monster_egg.json"))
    }

    @Test
    fun `drops the infested block loot table and keeps a normal block loot table`() {
        writeLootTable(
            tempDir,
            "blocks/infested_stone_bricks.json",
            """
            {
                "type": "minecraft:block",
                "pools": [
                    {
                        "conditions": [
                            {
                                "condition": "minecraft:match_tool",
                                "predicate": {
                                    "predicates": {
                                        "minecraft:enchantments": [
                                            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
                                        ]
                                    }
                                }
                            }
                        ],
                        "entries": [
                            {"type": "minecraft:item", "name": "minecraft:stone_bricks"}
                        ],
                        "rolls": 1.0
                    }
                ]
            }
            """.trimIndent()
        )
        writeLootTable(
            tempDir,
            "blocks/stone_bricks.json",
            """
            {
                "type": "minecraft:block",
                "pools": [
                    {
                        "entries": [
                            {"type": "minecraft:item", "name": "minecraft:stone_bricks"}
                        ],
                        "rolls": 1.0
                    }
                ]
            }
            """.trimIndent()
        )

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, contextFor(tempDir))

        assertEquals(1, sources.size, "Expected only the normal block loot table to survive, got: ${sources.map { it.filename }}")
        assertEquals("blocks/stone_bricks.json", sources.single().filename)
        assertEquals("minecraft:stone_bricks", sources.single().producedItems.single().first.id)
    }
}
