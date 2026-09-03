package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils
import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-491 — a loot table another table pulls in by name must not also be stored as a source of
 * its own. Its numbers are conditional on the parent rolling it, and nothing in the row says so.
 *
 * The fixture is Minecraft's fishing shape, cut down to numbers that can be checked by hand:
 * a parent that rolls treasure 5 times in 100, and a treasure table in which a nautilus shell
 * is 1 of 6. The composed per-cast probability is `0.05 * 1/6 = 0.008333…`, which is exactly
 * the 20x gap between the two rows the real 1.21.4 ingest stores today.
 */
class NestedLootTableTest {

    private val version = MinecraftVersion.Release(1, 21, 0)

    @TempDir
    lateinit var tempDir: Path

    private val itemIds = setOf(
        "minecraft:nautilus_shell", "minecraft:name_tag", "minecraft:cod",
        "minecraft:ink_sac", "minecraft:bone",
    )

    private fun context(): ExtractionContext = ExtractionContext(
        version = version,
        root = tempDir,
        names = itemIds.associateWith { "${it.substringAfter(':')} (Item)" },
        tags = emptyMap(),
        itemIds = itemIds,
    )

    private fun writeLootTable(relativePath: String, json: String) {
        val file = tempDir.resolve("loot_table").resolve(relativePath)
        file.parent.toFile().mkdirs()
        file.toFile().writeText(json)
    }

    /** Weight 5 of 100 on the treasure sub-table; the other 95 are an ordinary item entry. */
    private fun writeFishing() {
        writeLootTable(
            "gameplay/fishing.json",
            """
            {
              "type": "minecraft:fishing",
              "pools": [
                {
                  "rolls": 1.0,
                  "entries": [
                    {"type": "minecraft:item", "name": "minecraft:cod", "weight": 95},
                    {"type": "minecraft:loot_table", "value": "minecraft:gameplay/fishing/treasure", "weight": 5}
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        writeLootTable(
            "gameplay/fishing/treasure.json",
            """
            {
              "type": "minecraft:fishing",
              "pools": [
                {
                  "rolls": 1.0,
                  "entries": [
                    {"type": "minecraft:item", "name": "minecraft:nautilus_shell", "weight": 1},
                    {"type": "minecraft:item", "name": "minecraft:name_tag", "weight": 5}
                  ]
                }
              ]
            }
            """.trimIndent()
        )
    }

    private fun yieldOf(source: ResourceSource, itemId: String): Double {
        val quantity = source.producedItems.single { it.first.id == itemId }.second
        assertIs<ResourceQuantity.ExpectedYield>(quantity, "$itemId in ${source.filename}")
        return quantity.expected
    }

    @Test
    fun `a referenced sub-table is not stored as a source of its own`() {
        writeFishing()

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        assertNull(
            sources.find { it.filename == "gameplay/fishing/treasure.json" },
            "The treasure sub-table is conditional on the parent rolling it — storing it " +
                "standalone reads 1-in-6 per cast instead of 1-in-6 of a 5% roll. Got: " +
                sources.map { it.filename },
        )
        assertEquals(listOf("gameplay/fishing.json"), sources.map { it.filename })
    }

    @Test
    fun `the surviving parent carries the composed per-cast probability`() {
        writeFishing()

        val fishing = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())
            .single { it.filename == "gameplay/fishing.json" }

        // 5/100 of casts roll treasure; 1/6 of those are the shell, 5/6 the name tag.
        assertEquals(0.05 * 1.0 / 6.0, yieldOf(fishing, "minecraft:nautilus_shell"), 1e-9)
        assertEquals(0.05 * 5.0 / 6.0, yieldOf(fishing, "minecraft:name_tag"), 1e-9)
        assertEquals(0.95, yieldOf(fishing, "minecraft:cod"), 1e-9)
    }

    @Test
    fun `no stored source overstates the shell's per-cast rate`() {
        writeFishing()

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        val best = sources
            .flatMap { source -> source.producedItems.filter { it.first.id == "minecraft:nautilus_shell" } }
            .mapNotNull { (it.second as? ResourceQuantity.ExpectedYield)?.expected }
            .max()

        assertTrue(
            best <= 0.0084,
            "A consumer taking the best source per item must not see better than the real " +
                "per-cast rate 0.00833; saw $best",
        )
    }

    @Test
    fun `a sub-table referenced by two parents is dropped once and composed into both`() {
        writeLootTable(
            "gameplay/fishing.json",
            """
            {
              "type": "minecraft:fishing",
              "pools": [
                {
                  "rolls": 1.0,
                  "entries": [
                    {"type": "minecraft:item", "name": "minecraft:name_tag", "weight": 3},
                    {"type": "minecraft:loot_table", "value": "minecraft:gameplay/fishing/fish", "weight": 1}
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        writeLootTable(
            "entities/guardian.json",
            """
            {
              "type": "minecraft:entity",
              "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": "minecraft:ink_sac"}]},
                {"rolls": 1.0, "entries": [{"type": "minecraft:loot_table", "value": "minecraft:gameplay/fishing/fish"}]}
              ]
            }
            """.trimIndent()
        )
        writeLootTable(
            "gameplay/fishing/fish.json",
            """
            {
              "type": "minecraft:fishing",
              "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": "minecraft:cod"}]}
              ]
            }
            """.trimIndent()
        )

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        assertEquals(
            setOf("gameplay/fishing.json", "entities/guardian.json"),
            sources.map { it.filename }.toSet(),
            "The shared fish table has no single composed probability — 1/4 of a cast and 1 " +
                "per guardian — so it must be dropped, not restated under one of them.",
        )
        assertEquals(0.25, yieldOf(sources.single { it.filename == "gameplay/fishing.json" }, "minecraft:cod"), 1e-9)
        assertEquals(1.0, yieldOf(sources.single { it.filename == "entities/guardian.json" }, "minecraft:cod"), 1e-9)
    }

    @Test
    fun `an unreferenced table is kept even when it sits under a parent's directory`() {
        writeFishing()
        writeLootTable(
            "gameplay/fishing/junk.json",
            """
            {
              "type": "minecraft:fishing",
              "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": "minecraft:bone"}]}
              ]
            }
            """.trimIndent()
        )

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        assertEquals(
            setOf("gameplay/fishing.json", "gameplay/fishing/junk.json"),
            sources.map { it.filename }.toSet(),
            "Nothing references junk here, so the directory nesting alone must not drop it.",
        )
    }

    /**
     * The dispatch shape, which is *not* a dilution: `shearing/sheep.json` picks a child by the
     * sheep's colour, and the colour is the player's choice, not a roll. Shearing a white sheep
     * always gives 2 white wool — exactly what the child says — while the parent can only keep
     * the last alternative's yield and marks the rest unknown. Dropping these replaced fifteen
     * exact wool yields with fifteen shrugs and doubled the modelled cost of coloured wool, so
     * this is pinned rather than left to the next person to rediscover.
     */
    @Test
    fun `an alternatives dispatch keeps its children`() {
        writeLootTable(
            "shearing/sheep.json",
            """
            {
              "type": "minecraft:shearing",
              "pools": [
                {
                  "rolls": 1.0,
                  "entries": [
                    {
                      "type": "minecraft:alternatives",
                      "children": [
                        {
                          "type": "minecraft:loot_table",
                          "conditions": [{"condition": "minecraft:entity_properties"}],
                          "value": "minecraft:shearing/sheep/white"
                        },
                        {"type": "minecraft:loot_table", "value": "minecraft:shearing/sheep/black"}
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        listOf("white" to "minecraft:ink_sac", "black" to "minecraft:bone").forEach { (colour, item) ->
            writeLootTable(
                "shearing/sheep/$colour.json",
                """
                {
                  "type": "minecraft:shearing",
                  "pools": [
                    {"rolls": 2.0, "entries": [{"type": "minecraft:item", "name": "$item"}]}
                  ]
                }
                """.trimIndent()
            )
        }

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        assertEquals(
            setOf("shearing/sheep.json", "shearing/sheep/white.json", "shearing/sheep/black.json"),
            sources.map { it.filename }.toSet(),
            "a colour dispatch is a choice, not a roll — the children carry the honest yields",
        )
        assertEquals(2.0, yieldOf(sources.single { it.filename == "shearing/sheep/white.json" }, "minecraft:ink_sac"), 1e-9)
    }

    /**
     * A reference that carries its pool's whole weight is composed losslessly, so the child is
     * an honest standalone source. Pre-1.21.2 data is exactly this — each
     * `entities/sheep/<colour>.json` includes the shared `entities/sheep.json`, and killing a
     * sheep really does drop mutton.
     */
    @Test
    fun `a whole-pool inclusion keeps the included table`() {
        writeLootTable(
            "entities/sheep/white.json",
            """
            {
              "type": "minecraft:entity",
              "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": "minecraft:ink_sac"}]},
                {"rolls": 1.0, "entries": [{"type": "minecraft:loot_table", "name": "minecraft:entities/sheep"}]}
              ]
            }
            """.trimIndent()
        )
        writeLootTable(
            "entities/sheep.json",
            """
            {
              "type": "minecraft:entity",
              "pools": [
                {"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": "minecraft:bone"}]}
              ]
            }
            """.trimIndent()
        )

        val sources = TestUtils.executeAndAssertSuccess(ExtractLootTables, context())

        assertEquals(
            setOf("entities/sheep/white.json", "entities/sheep.json"),
            sources.map { it.filename }.toSet(),
        )
        assertEquals(1.0, yieldOf(sources.single { it.filename == "entities/sheep.json" }, "minecraft:bone"), 1e-9)
        assertEquals(1.0, yieldOf(sources.single { it.filename == "entities/sheep/white.json" }, "minecraft:bone"), 1e-9)
    }

    @Test
    fun `dilution is inherited by references nested inside a weighted entry`() {
        val nested = Json.parseToJsonElement(
            """
            {
              "type": "minecraft:block",
              "pools": [
                {
                  "entries": [
                    {"type": "minecraft:item", "name": "minecraft:cod", "weight": 95},
                    {
                      "type": "minecraft:alternatives",
                      "weight": 5,
                      "children": [
                        {"type": "minecraft:loot_table", "value": "minecraft:gameplay/fishing/treasure"},
                        {"type": "minecraft:item", "name": "minecraft:cod"}
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(setOf("gameplay/fishing/treasure.json"), LootTableReferences.dilutedIn(nested))
    }

    @Test
    fun `a bare item id in a loot_table entry is not treated as a table reference`() {
        val bareItem = Json.parseToJsonElement(
            """
            {
              "type": "minecraft:chest",
              "pools": [
                {
                  "entries": [
                    {"type": "minecraft:loot_table", "value": "minecraft:cod", "weight": 1},
                    {"type": "minecraft:item", "name": "minecraft:bone", "weight": 9}
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(emptySet(), LootTableReferences.dilutedIn(bareItem))
    }
}
