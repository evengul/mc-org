package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaFilterParserTest {

    @Test
    fun `parse empty parameters returns empty filters`() {
        val params = Parameters.Empty
        val filters = IdeaFilterParser.parse(params)

        assertNull(filters.query)
        assertNull(filters.category)
        assertTrue(filters.difficulties.isEmpty())
        assertNull(filters.minRating)
        assertNull(filters.minecraftVersion)
        assertTrue(filters.categoryFilters.isEmpty())
    }

    @Test
    fun `parse query parameter`() {
        val params = parametersOf("query" to listOf("iron farm"))
        val filters = IdeaFilterParser.parse(params)

        assertEquals("iron farm", filters.query)
    }

    @Test
    fun `parse category parameter`() {
        val params = parametersOf("category" to listOf("FARM"))
        val filters = IdeaFilterParser.parse(params)

        assertEquals(IdeaCategory.FARM, filters.category)
    }

    @Test
    fun `parse multiple difficulty filters`() {
        val params = parametersOf(
            "difficulty[]" to listOf("END_GAME", "TECHNICAL_UNDERSTANDING_REQUIRED")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals(2, filters.difficulties.size)
        assertTrue(filters.difficulties.contains(IdeaDifficulty.END_GAME))
        assertTrue(filters.difficulties.contains(IdeaDifficulty.TECHNICAL_UNDERSTANDING_REQUIRED))
    }

    @Test
    fun `parse minimum rating`() {
        val params = parametersOf("minRating" to listOf("4.5"))
        val filters = IdeaFilterParser.parse(params)

        assertEquals(4.5, filters.minRating)
    }

    @Test
    fun `parse minecraft version`() {
        val params = parametersOf("minecraftVersion" to listOf("1.20.1"))
        val filters = IdeaFilterParser.parse(params)

        assertEquals("1.20.1", filters.minecraftVersion)
    }

    @Test
    fun `parse boolean category filter`() {
        val params = parametersOf(
            "category" to listOf("FARM"),
            "categoryFilters[afkable]" to listOf("true")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals(1, filters.categoryFilters.size)
        val afkableFilter = filters.categoryFilters["afkable"]
        assertTrue(afkableFilter is FilterValue.BooleanValue)
        assertEquals(true, afkableFilter.value)
    }

    @Test
    fun `parse number range category filter`() {
        val params = parametersOf(
            "category" to listOf("FARM"),
            "categoryFilters[productionRate_min]" to listOf("1000"),
            "categoryFilters[productionRate_max]" to listOf("5000")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals(1, filters.categoryFilters.size)
        val rateFilter = filters.categoryFilters["productionRate"]
        assertTrue(rateFilter is FilterValue.NumberRange)
        assertEquals(1000.0, rateFilter.min)
        assertEquals(5000.0, rateFilter.max)
    }

    @Test
    fun `parse select category filter`() {
        val params = parametersOf(
            "category" to listOf("FARM"),
            "categoryFilters[playersRequired]" to listOf("1")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals(1, filters.categoryFilters.size)
        val playersFilter = filters.categoryFilters["playersRequired"]
        assertTrue(playersFilter is FilterValue.SelectValue)
        assertEquals("1", playersFilter.value)
    }

    @Test
    fun `parse multi-select category filter`() {
        val params = parametersOf(
            "category" to listOf("FARM"),
            "categoryFilters[biomes][]" to listOf("Plains", "Forest", "Desert")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals(1, filters.categoryFilters.size)
        val biomesFilter = filters.categoryFilters["biomes"]
        assertTrue(biomesFilter is FilterValue.MultiSelectValue)
        val values = biomesFilter.values
        assertEquals(3, values.size)
        assertTrue(values.contains("Plains"))
        assertTrue(values.contains("Forest"))
        assertTrue(values.contains("Desert"))
    }

    @Test
    fun `parse combined filters`() {
        val params = parametersOf(
            "query" to listOf("iron"),
            "category" to listOf("FARM"),
            "difficulty[]" to listOf("START_OF_GAME", "END_GAME"),
            "minRating" to listOf("4.0"),
            "categoryFilters[afkable]" to listOf("true"),
            "categoryFilters[productionRate_min]" to listOf("10000")
        )
        val filters = IdeaFilterParser.parse(params)

        assertEquals("iron", filters.query)
        assertEquals(IdeaCategory.FARM, filters.category)
        assertEquals(2, filters.difficulties.size)
        assertEquals(4.0, filters.minRating)
        assertEquals(2, filters.categoryFilters.size)
    }

    @Test
    fun `ignore invalid category`() {
        val params = parametersOf("category" to listOf("INVALID_CATEGORY"))
        val filters = IdeaFilterParser.parse(params)

        assertNull(filters.category)
    }

    @Test
    fun `ignore invalid difficulty`() {
        val params = parametersOf("difficulty[]" to listOf("END_GAME", "INVALID_DIFFICULTY"))
        val filters = IdeaFilterParser.parse(params)

        assertEquals(1, filters.difficulties.size)
        assertEquals(IdeaDifficulty.END_GAME, filters.difficulties.first())
    }

    @Test
    fun `ignore invalid rating value`() {
        val params = parametersOf("minRating" to listOf("not_a_number"))
        val filters = IdeaFilterParser.parse(params)

        assertNull(filters.minRating)
    }

    @Test
    fun `coerce rating to valid range`() {
        val paramsLow = parametersOf("minRating" to listOf("-1.0"))
        val filtersLow = IdeaFilterParser.parse(paramsLow)
        assertEquals(0.0, filtersLow.minRating)

        val paramsHigh = parametersOf("minRating" to listOf("10.0"))
        val filtersHigh = IdeaFilterParser.parse(paramsHigh)
        assertEquals(5.0, filtersHigh.minRating)
    }

    @Test
    fun `ignore category filters when no category selected`() {
        val params = parametersOf(
            "categoryFilters[afkable]" to listOf("true")
        )
        val filters = IdeaFilterParser.parse(params)

        assertTrue(filters.categoryFilters.isEmpty())
    }
}

