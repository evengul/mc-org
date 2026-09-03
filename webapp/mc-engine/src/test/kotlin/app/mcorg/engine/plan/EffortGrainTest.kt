package app.mcorg.engine.plan

import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-494: effort is priced per *source*, not per source type.
 *
 * The type says what the action costs; the source says what reaching it costs. One number used
 * to cover 995 block-loot sources, so mining dirt, emerald ore and ancient debris all came to
 * 0.05 minutes and the model reasoned impeccably from a false premise.
 *
 * These tests pin the shape of the split rather than the numbers themselves — the numbers are
 * estimates of an unmodelled quantity and are meant to be argued with. What must not drift is
 * that the split exists, that it keys on the right thing, and that it stays small.
 */
class EffortGrainTest {

    private val table = EffortTable.DEFAULT

    private fun source(type: SourceType, filename: String) =
        SourceNode.fromKey("${type.id}:$filename")

    private fun block(name: String) = source(SourceType.LootTypes.BLOCK, "blocks/$name.json")
    private fun chest(path: String) = source(SourceType.LootTypes.CHEST, "chests/$path.json")

    // ── the split exists ────────────────────────────────────────────────────

    @Test
    fun `ancient debris costs far more to mine than dirt`() {
        val dirt = table.of(block("dirt"))
        val debris = table.of(block("ancient_debris"))

        assertTrue(
            debris > dirt * 50,
            "the swing is the same; the finding is not. dirt=$dirt debris=$debris"
        )
    }

    @Test
    fun `ore sits between plain stone and ancient debris`() {
        val stone = table.of(block("stone"))
        val iron = table.of(block("iron_ore"))
        val diamond = table.of(block("diamond_ore"))
        val debris = table.of(block("ancient_debris"))

        assertTrue(stone < iron, "an ore vein takes finding, plain stone does not")
        assertTrue(iron < diamond, "diamond is rarer than iron")
        assertTrue(diamond < debris, "and debris is rarer than diamond")
    }

    @Test
    fun `an unlisted block is priced as the bare action`() {
        // The table's default has to be "no finding cost", or every block not thought about
        // becomes silently dearer than the ones that were.
        assertEquals(
            table.of(SourceType.LootTypes.BLOCK),
            table.of(block("cobblestone")),
            1e-12,
        )
    }

    // ── it keys on the right thing ──────────────────────────────────────────

    @Test
    fun `a chest is priced by its structure, not by its filename`() {
        // The regression this pins: keying on the exact stem priced `reward_ominous` and pushed
        // five items onto `reward_ominous_unique`, a sibling chest in the same room with no
        // entry of its own. A structure is one errand however many chests are in it.
        assertEquals(
            table.of(chest("trial_chambers/reward_ominous")),
            table.of(chest("trial_chambers/reward_ominous_unique")),
            1e-12,
            "two chests in the same structure cost the same to reach",
        )
        assertTrue(
            table.of(chest("ancient_city")) > table.of(chest("village/village_desert_house")),
            "and an ancient city is a longer journey than a village you are standing in",
        )
    }

    @Test
    fun `trade level is read from the path, because it is the one fact the data states`() {
        val novice = table.of(source(SourceType.TradeTypes.CLERIC, "cleric/1/emerald_to_rotten_flesh.json"))
        val master = table.of(source(SourceType.TradeTypes.CLERIC, "cleric/5/emerald_to_bottle_o_enchanting.json"))

        assertTrue(
            master > novice,
            "unlocking a master villager is most of what a master trade costs",
        )
        assertEquals(
            table.of(SourceType.TradeTypes.CLERIC),
            novice,
            1e-12,
            "a novice trade is the bare price — the level scales up from there, not down",
        )
    }

    @Test
    fun `a chicken laying an egg is not a villager's post-raid gift`() {
        // GIFT covers both, and calibration set it to ten minutes, which is about right for the
        // raid and absurd for the chicken. The chicken is the one plans depend on.
        assertTrue(
            table.of(source(SourceType.LootTypes.GIFT, "gameplay/chicken_lay.json")) < 1.0,
            "standing near a chicken is not a ten-minute errand",
        )
    }

    // ── and it stays small ──────────────────────────────────────────────────

    @Test
    fun `the coarse grain is still available, so the two can be diffed`() {
        val coarse = EffortTable.DEFAULT.typeOnly()

        assertEquals(
            coarse.of(SourceType.LootTypes.BLOCK),
            coarse.of(block("ancient_debris")),
            1e-12,
            "typeOnly is the pre-MCO-494 behaviour exactly, which is what makes the diff honest",
        )
        assertTrue(
            EffortTable.DEFAULT.of(block("ancient_debris")) > coarse.of(block("ancient_debris")),
            "and the per-source table really does differ from it",
        )
    }
}
