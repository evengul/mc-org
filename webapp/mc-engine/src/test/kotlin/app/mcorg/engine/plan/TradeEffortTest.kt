package app.mcorg.engine.plan

import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-524: a villager trade is priced as a **transaction**, not as the villager.
 *
 * There was no engine test touching trades at all — MCO-490 recorded that `isTrade()` was never
 * exercised by anything — which is how a number meaning "the whole trading hall, charged again on
 * every item you buy from it" survived. It made trades win **4 items in a 1522-item graph** in a
 * game where trading is one of the main ways players get things, and left `emerald` merely tied
 * with mining the rarest ore in the game.
 *
 * These pin the *shape* of the pricing, not the constants. The numbers are estimates of an
 * unmodelled quantity and are meant to be argued with; what must not drift is the meaning.
 */
class TradeEffortTest {

    private val table = EffortTable.DEFAULT

    private fun trade(type: SourceType, filename: String) =
        SourceNode.fromKey("${type.id}:$filename")

    private fun farmer(level: Int, name: String) =
        trade(SourceType.TradeTypes.FARMER, "farmer/$level/$name.json")

    @Test
    fun `a trade costs a transaction, not the villager behind it`() {
        // The regression this exists to catch is someone re-amortising villager setup into this
        // number "so trades are not too cheap". Setting a villager up is a fixed cost paid once
        // per villager; dividing it by one trade charges it to every item ever bought.
        //
        // A minute is already generous for walking to a hall, trading and waiting on restock.
        // Anything approaching the several minutes it takes to cure and level a villager means
        // the setup has crept back in.
        val cost = table.of(SourceType.TradeTypes.FARMER)

        assertTrue(
            cost <= 1.5,
            "a trade is a transaction; $cost min has villager setup folded back into it",
        )
    }

    @Test
    fun `every villager profession costs the same to trade with`() {
        // One number per profession is defensible and one per profession *difference* is not —
        // nothing about the game makes an armorer slower to trade with than a shepherd. What
        // does differ is level, which is priced separately from the source filename.
        val professions = listOf(
            SourceType.TradeTypes.ARMORER, SourceType.TradeTypes.BUTCHER,
            SourceType.TradeTypes.CARTOGRAPHER, SourceType.TradeTypes.CLERIC,
            SourceType.TradeTypes.FARMER, SourceType.TradeTypes.FISHERMAN,
            SourceType.TradeTypes.FLETCHER, SourceType.TradeTypes.LEATHERWORKER,
            SourceType.TradeTypes.LIBRARIAN, SourceType.TradeTypes.MASON,
            SourceType.TradeTypes.SHEPHERD, SourceType.TradeTypes.SMITH,
            SourceType.TradeTypes.TOOLSMITH, SourceType.TradeTypes.WEAPONSMITH,
        )

        assertEquals(
            1,
            professions.map { table.of(it) }.distinct().size,
            "professions must not drift apart: ${professions.associate { it.id to table.of(it) }}",
        )
    }

    @Test
    fun `a wandering trader costs more than a villager you keep`() {
        // Not a transaction price at all: it is waiting for a random trader to turn up, which no
        // amount of setup makes cheaper. Pricing it like a villager let `emerald` win by selling
        // a water bucket to a passing trader — real in the data, a fluke in practice.
        val villager = table.of(SourceType.TradeTypes.FARMER)
        val wandering = table.of(SourceType.TradeTypes.WANDERING_TRADER)

        assertTrue(
            wandering > villager,
            "a trader you cannot summon must not be as cheap as one you own " +
                "(wandering=$wandering villager=$villager)",
        )
    }

    @Test
    fun `unlocking a master villager costs more than trading with a novice`() {
        // The level is the one part of a trade's cost the data states outright rather than us
        // guessing, and it is read from the filename. Level 5 trades win real items (experience
        // bottles, tipped arrows); at a flat price they would win them at novice cost.
        val novice = table.of(farmer(1, "carrot_emerald"))
        val master = table.of(farmer(5, "golden_carrot_emerald"))

        assertTrue(master > novice, "novice=$novice master=$master")
    }

    @Test
    fun `level scales monotonically`() {
        val byLevel = (1..5).map { table.of(farmer(it, "carrot_emerald")) }

        assertEquals(
            byLevel.sorted(),
            byLevel,
            "a higher tier must never be cheaper to unlock: $byLevel",
        )
    }

    @Test
    fun `a trade with no level in its path is priced as the bare transaction`() {
        // Wandering-trader files carry no level segment, and neither would a malformed path. The
        // fallback has to be "no unlocking cost" or anything unparsed becomes silently dearer.
        assertEquals(
            table.of(SourceType.TradeTypes.FARMER),
            table.of(trade(SourceType.TradeTypes.FARMER, "farmer/carrot_emerald.json")),
        )
    }
}
