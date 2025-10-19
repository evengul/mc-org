package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetIdeasByCategoryStepTest : WithUser() {

    @BeforeEach
    fun setup() {
        // Clean ideas data before each test
        runBlocking {
            DatabaseSteps.update<Unit, DatabaseFailure>(
                sql = SafeSQL.delete("DELETE FROM ideas"),
                parameterSetter = { _, _ -> },
                errorMapper = { it }
            ).process(Unit)
        }
    }

    @Test
    fun `should return all ideas when no filters applied`() = runBlocking {
        // Given: Multiple ideas in database
        createTestIdea(name = "Iron Farm", category = IdeaCategory.FARM)
        createTestIdea(name = "Gold Farm", category = IdeaCategory.FARM)
        createTestIdea(name = "Storage System", category = IdeaCategory.STORAGE)

        // When: Searching with no filters
        val filters = IdeaSearchFilters()
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: All ideas returned
        assertTrue(result is Result.Success)
        assertEquals(3, result.value.size)
    }

    @Test
    fun `should filter by category`() = runBlocking {
        // Given: Ideas from different categories
        createTestIdea(name = "Iron Farm", category = IdeaCategory.FARM)
        createTestIdea(name = "Gold Farm", category = IdeaCategory.FARM)
        createTestIdea(name = "Storage System", category = IdeaCategory.STORAGE)

        // When: Filtering by FARM category
        val filters = IdeaSearchFilters(category = IdeaCategory.FARM)
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only FARM ideas returned
        assertTrue(result is Result.Success)
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.category == IdeaCategory.FARM })
    }

    @Test
    fun `should filter by difficulty`() = runBlocking {
        // Given: Ideas with different difficulties
        createTestIdea(name = "Easy Farm", difficulty = IdeaDifficulty.START_OF_GAME)
        createTestIdea(name = "Normal Farm", difficulty = IdeaDifficulty.MID_GAME)
        createTestIdea(name = "Hard Farm", difficulty = IdeaDifficulty.END_GAME)

        // When: Filtering by START_OF_GAME and MID_GAME
        val filters = IdeaSearchFilters(
            difficulties = listOf(IdeaDifficulty.START_OF_GAME, IdeaDifficulty.MID_GAME)
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only START_OF_GAME and MID_GAME ideas returned
        assertTrue(result is Result.Success)
        assertEquals(2, result.value.size)
        assertTrue(result.value.all { it.difficulty in listOf(IdeaDifficulty.START_OF_GAME, IdeaDifficulty.MID_GAME) })
    }

    @Test
    fun `should filter by minimum rating`() = runBlocking {
        // Given: Ideas with different ratings
        createTestIdea(name = "Low Rated", ratingAverage = 2.5)
        createTestIdea(name = "Medium Rated", ratingAverage = 3.8)
        createTestIdea(name = "High Rated", ratingAverage = 4.5)

        // When: Filtering by minimum rating of 4.0
        val filters = IdeaSearchFilters(minRating = 4.0)
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only ideas with rating >= 4.0 returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("High Rated", result.value.first().name)
    }

    @Test
    fun `should perform full-text search on name and description`() = runBlocking {
        // Given: Ideas with different content
        createTestIdea(name = "Iron Farm", description = "Produces iron ingots")
        createTestIdea(name = "Gold Farm", description = "Produces gold ingots")
        createTestIdea(name = "Storage System", description = "Stores items efficiently")

        // When: Searching for "iron"
        val filters = IdeaSearchFilters(query = "iron")
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only iron-related ideas returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("Iron Farm", result.value.first().name)
    }

    @Test
    fun `should filter by boolean category field`() = runBlocking {
        // Given: Farm ideas with different afkable values
        createTestIdea(
            name = "AFK Iron Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"afkable": true, "productionRate": 1000}"""
        )
        createTestIdea(
            name = "Manual Iron Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"afkable": false, "productionRate": 2000}"""
        )

        // When: Filtering for AFK-able farms
        val filters = IdeaSearchFilters(
            category = IdeaCategory.FARM,
            categoryFilters = mapOf("afkable" to FilterValue.BooleanValue(true))
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only AFK-able farm returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("AFK Iron Farm", result.value.first().name)
    }

    @Test
    fun `should filter by number range in category field`() = runBlocking {
        // Given: Farm ideas with different production rates
        createTestIdea(
            name = "Slow Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"productionRate": 500}"""
        )
        createTestIdea(
            name = "Medium Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"productionRate": 5000}"""
        )
        createTestIdea(
            name = "Fast Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"productionRate": 15000}"""
        )

        // When: Filtering for production rate between 1000 and 10000
        val filters = IdeaSearchFilters(
            category = IdeaCategory.FARM,
            categoryFilters = mapOf("productionRate" to FilterValue.NumberRange(1000.0, 10000.0))
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only medium farm returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("Medium Farm", result.value.first().name)
    }

    @Test
    fun `should filter by select field`() = runBlocking {
        // Given: Farm ideas with different player requirements
        createTestIdea(
            name = "Auto Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"playersRequired": "0 (Automatic)"}"""
        )
        createTestIdea(
            name = "Solo Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"playersRequired": "1"}"""
        )
        createTestIdea(
            name = "Co-op Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"playersRequired": "2"}"""
        )

        // When: Filtering for single player farms
        val filters = IdeaSearchFilters(
            category = IdeaCategory.FARM,
            categoryFilters = mapOf("playersRequired" to FilterValue.SelectValue("1"))
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only solo farm returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("Solo Farm", result.value.first().name)
    }

    @Test
    fun `should filter by multi-select field`() = runBlocking {
        // Given: Farm ideas with different biome compatibility
        createTestIdea(
            name = "Plains Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"biomes": ["Plains", "Forest"]}"""
        )
        createTestIdea(
            name = "Desert Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"biomes": ["Desert", "Savanna"]}"""
        )
        createTestIdea(
            name = "Universal Farm",
            category = IdeaCategory.FARM,
            categoryData = """{"biomes": ["Plains", "Desert", "Forest"]}"""
        )

        // When: Filtering for farms that work in Plains or Desert
        val filters = IdeaSearchFilters(
            category = IdeaCategory.FARM,
            categoryFilters = mapOf("biomes" to FilterValue.MultiSelectValue(listOf("Plains", "Desert")))
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: All three farms returned (each contains at least one matching biome)
        assertTrue(result is Result.Success)
        assertEquals(3, result.value.size)
    }

    @Test
    fun `should filter by text field with case-insensitive search`() = runBlocking {
        // Given: Storage ideas with different types
        createTestIdea(
            name = "Item Sorter A",
            category = IdeaCategory.STORAGE,
            categoryData = """{"type": "item sorter"}"""
        )
        createTestIdea(
            name = "Box Loader",
            category = IdeaCategory.STORAGE,
            categoryData = """{"type": "loader"}"""
        )

        // When: Searching for "SORTER" (case-insensitive)
        val filters = IdeaSearchFilters(
            category = IdeaCategory.STORAGE,
            categoryFilters = mapOf("type" to FilterValue.TextValue("sorter"))
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Item sorter returned
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("Item Sorter A", result.value.first().name)
    }

    @Test
    fun `should combine multiple filters`() = runBlocking {
        // Given: Multiple farms with various attributes
        createTestIdea(
            name = "Perfect Iron Farm",
            category = IdeaCategory.FARM,
            difficulty = IdeaDifficulty.START_OF_GAME,
            ratingAverage = 4.8,
            categoryData = """{"afkable": true, "productionRate": 10000}"""
        )
        createTestIdea(
            name = "Manual Iron Farm",
            category = IdeaCategory.FARM,
            difficulty = IdeaDifficulty.START_OF_GAME,
            ratingAverage = 4.2,
            categoryData = """{"afkable": false, "productionRate": 12000}"""
        )
        createTestIdea(
            name = "Complex Gold Farm",
            category = IdeaCategory.FARM,
            difficulty = IdeaDifficulty.END_GAME,
            ratingAverage = 4.5,
            categoryData = """{"afkable": true, "productionRate": 8000}"""
        )

        // When: Applying multiple filters
        val filters = IdeaSearchFilters(
            category = IdeaCategory.FARM,
            difficulties = listOf(IdeaDifficulty.START_OF_GAME),
            minRating = 4.5,
            categoryFilters = mapOf(
                "afkable" to FilterValue.BooleanValue(true),
                "productionRate" to FilterValue.NumberRange(9000.0, null)
            )
        )
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Only the perfect iron farm matches all criteria
        assertTrue(result is Result.Success)
        assertEquals(1, result.value.size)
        assertEquals("Perfect Iron Farm", result.value.first().name)
    }

    @Test
    fun `should return empty list when no ideas match filters`() = runBlocking {
        // Given: Some ideas in database
        createTestIdea(name = "Iron Farm", category = IdeaCategory.FARM)

        // When: Filtering for non-existent category
        val filters = IdeaSearchFilters(category = IdeaCategory.CART_TECH)
        val result = GetIdeasByCategoryStep.process(GetIdeasByCategoryInput(filters))

        // Then: Empty list returned
        assertTrue(result is Result.Success)
        assertEquals(0, result.value.size)
    }

    /**
     * Helper function to create test ideas
     */
    private suspend fun createTestIdea(
        name: String = "Test Idea",
        description: String = "Test description",
        category: IdeaCategory = IdeaCategory.FARM,
        difficulty: IdeaDifficulty = IdeaDifficulty.MID_GAME,
        ratingAverage: Double = 0.0,
        categoryData: String = "{}"
    ) {
        val sql = SafeSQL.insert("""
            INSERT INTO ideas (
                name, description, category, author, difficulty, 
                minecraft_version_range, category_data, created_by, 
                rating_average, rating_count
            ) VALUES (?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?, 0)
        """.trimIndent())

        DatabaseSteps.update<Unit, DatabaseFailure>(
            sql = sql,
            parameterSetter = { statement, _ ->
                statement.setString(1, name)
                statement.setString(2, description)
                statement.setString(3, category.name)
                statement.setString(4, """{"type": "single", "name": "TestAuthor"}""")
                statement.setString(5, difficulty.name)
                statement.setString(6, """{"type": "unbounded"}""")
                statement.setString(7, categoryData)
                statement.setInt(8, user.id)
                statement.setDouble(9, ratingAverage)
            },
            errorMapper = { it }
        ).process(Unit).getOrNull() ?: error("Failed to create test idea")
    }
}
