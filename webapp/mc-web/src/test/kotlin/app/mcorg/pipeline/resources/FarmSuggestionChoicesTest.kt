package app.mcorg.pipeline.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-483 — designs covering the same demand are one choice, not several rows.
 *
 * The case that filed this is at the top: the YAMS plan's *71k Ice Farm* and *72k Ice Farm*,
 * both claiming the same 20,611 Ice, offered as two peer checkboxes under "Review selected
 * designs". The rest pin the two decisions the grouping rests on — what "same demand" means,
 * and what makes one design the recommendation.
 */
class FarmSuggestionChoicesTest {

    private fun covered(itemId: String, name: String, quantity: Long, rate: Int?) =
        CoveredDemand(itemId, name, quantity, rate)

    private fun design(
        id: Int,
        name: String,
        produces: List<CoveredDemand>,
        alsoRemoves: List<CoveredDemand> = emptyList(),
    ) = FarmSuggestion(id, name, produces, alsoRemoves)

    private fun ice(rate: Int?) = listOf(covered("minecraft:ice", "Ice", 20_611, rate))

    // ---- what "same demand" means ---------------------------------------------------

    @Test
    fun `two designs covering the same item are one choice`() {
        val choices = FarmSuggestionChoices.of(
            listOf(
                design(1, "71k Ice Farm", ice(71_000)),
                design(2, "72k Ice Farm", ice(72_000)),
            )
        )

        assertEquals(1, choices.size, "one demand, one row: $choices")
        val choice = choices.single()
        assertEquals(setOf("minecraft:ice"), choice.coveredItemIds)
        assertEquals(1, choice.alternatives.size, "the other design is an alternative, not a peer")
        assertEquals(2, choice.designs.size)
    }

    @Test
    fun `designs covering different items stay separate choices`() {
        val choices = FarmSuggestionChoices.of(
            listOf(
                design(1, "Ice Farm", ice(72_000)),
                design(2, "Sand Duper", listOf(covered("minecraft:sand", "Sand", 5_436, 45_000))),
            )
        )

        assertEquals(2, choices.size)
        assertTrue(choices.all { it.alternatives.isEmpty() })
    }

    @Test
    fun `partial overlap is not grouped, and keys on the whole set`() {
        // The harder case, deliberately out of scope for this pass: a design covering Ice *and*
        // Packed Ice is not obviously interchangeable with one covering only Ice. The key being
        // the full set is what keeps the door open — merging intersecting keys later is a change
        // to this function, not to the row shape or the selection rule.
        val both = design(
            1, "Ice and Packed Ice Farm",
            listOf(
                covered("minecraft:ice", "Ice", 20_611, 40_000),
                covered("minecraft:packed_ice", "Packed Ice", 2_000, 4_000),
            ),
        )
        val iceOnly = design(2, "72k Ice Farm", ice(72_000))

        val choices = FarmSuggestionChoices.of(listOf(both, iceOnly))

        assertEquals(2, choices.size, "not grouped while nothing can rank partial against total")
        assertEquals(
            setOf(setOf("minecraft:ice", "minecraft:packed_ice"), setOf("minecraft:ice")),
            choices.mapTo(mutableSetOf()) { it.coveredItemIds },
        )
    }

    @Test
    fun `the knock-on does not enter the key`() {
        // alsoRemoves is derived from the direct set and the plan, so identical designs always
        // share it. Keying on it could only invent a way for them to key apart.
        val a = design(1, "Iron Farm A", listOf(covered("minecraft:iron_ingot", "Iron Ingot", 33_049, 3_810)))
        val b = design(
            2, "Iron Farm B",
            listOf(covered("minecraft:iron_ingot", "Iron Ingot", 33_049, 3_900)),
            alsoRemoves = listOf(covered("minecraft:deepslate_iron_ore", "Deepslate Iron Ore", 33_049, null)),
        )

        val choices = FarmSuggestionChoices.of(listOf(a, b))

        assertEquals(1, choices.size, "same produced demand, one choice: $choices")
    }

    // ---- what makes one design the recommendation ------------------------------------

    @Test
    fun `the fastest design leads, and says by how much`() {
        val choice = FarmSuggestionChoices.of(
            listOf(
                design(1, "71k Ice Farm", ice(71_000)),
                design(2, "72k Ice Farm", ice(72_000)),
            )
        ).single()

        assertEquals("72k Ice Farm", choice.recommended.ideaName)
        val reason = choice.reason
        assertIs<RecommendationReason.Fastest>(reason)
        assertTrue(
            reason.hours < reason.runnerUpHours,
            "the runner-up's time rides along so the row can show a 1.4% gap as the coin toss it is",
        )
    }

    @Test
    fun `an unmeasured design never outranks a measured one`() {
        // "Unknown" is not "instant". A design nobody timed has the least evidence behind it and
        // is the worst possible thing to recommend on speed.
        val choice = FarmSuggestionChoices.of(
            listOf(
                design(1, "Ancient Ice Farm", ice(null)),
                design(2, "72k Ice Farm", ice(72_000)),
            )
        ).single()

        assertEquals("72k Ice Farm", choice.recommended.ideaName)
        assertIs<RecommendationReason.OnlyMeasured>(choice.reason)
    }

    @Test
    fun `with no rates at all the order is alphabetical and the row says so`() {
        val choice = FarmSuggestionChoices.of(
            listOf(
                design(2, "Zed's Ice Farm", ice(null)),
                design(1, "Ancient Ice Farm", ice(null)),
            )
        ).single()

        assertEquals("Ancient Ice Farm", choice.recommended.ideaName)
        val reason = choice.reason
        assertIs<RecommendationReason.NoFasterOption>(reason)
        assertEquals(null, reason.hours, "there is no time to print, and none is invented")
    }

    @Test
    fun `equal coverage times are not dressed up as a winner`() {
        val choice = FarmSuggestionChoices.of(
            listOf(
                design(2, "Zed's Ice Farm", ice(72_000)),
                design(1, "Ancient Ice Farm", ice(72_000)),
            )
        ).single()

        assertEquals("Ancient Ice Farm", choice.recommended.ideaName, "tie broken by name, stably")
        val reason = choice.reason
        assertIs<RecommendationReason.NoFasterOption>(reason)
        assertTrue(reason.hours != null, "they do cover it in a known time — the same one")
    }

    @Test
    fun `a lone design has nothing to explain`() {
        val choice = FarmSuggestionChoices.of(listOf(design(1, "72k Ice Farm", ice(72_000)))).single()

        assertEquals(RecommendationReason.Sole, choice.reason)
        assertTrue(choice.alternatives.isEmpty())
    }

    @Test
    fun `coverage time is the slowest measured line, since one farm makes them all at once`() {
        val slowBottles = design(
            1, "Witch Hut Farm",
            listOf(
                covered("minecraft:redstone", "Redstone Dust", 63_273, 8_280),   // ~7.6h
                covered("minecraft:glass_bottle", "Glass Bottle", 21_600, 785),  // ~27.5h
            ),
        )

        val hours = slowBottles.coverageHours!!

        assertTrue(hours > 27.0 && hours < 28.0, "the longest line is when everything is covered, got $hours")
    }

    // ---- ordering of the choices themselves ------------------------------------------

    @Test
    fun `choices keep the units-removed order the ungrouped list had`() {
        val choices = FarmSuggestionChoices.of(
            listOf(
                design(1, "Sand Duper", listOf(covered("minecraft:sand", "Sand", 5_436, 45_000))),
                design(2, "71k Ice Farm", ice(71_000)),
                design(3, "72k Ice Farm", ice(72_000)),
            )
        )

        assertEquals(
            listOf("72k Ice Farm", "Sand Duper"),
            choices.map { it.recommended.ideaName },
            "grouping decides who speaks for a demand; it does not reorder the demands",
        )
    }

    @Test
    fun `nothing to suggest is no choices`() {
        assertEquals(emptyList(), FarmSuggestionChoices.of(emptyList()))
    }
}
