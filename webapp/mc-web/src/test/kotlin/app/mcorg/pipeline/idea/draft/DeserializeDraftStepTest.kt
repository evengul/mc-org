package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeserializeDraftStepTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun makeDraft(dataJson: String) = IdeaDraft(
        id = 1,
        userId = 1,
        data = dataJson,
        currentStage = "REVIEW",
        sourceIdeaId = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now()
    )

    /**
     * Build a valid DraftData JSON by serializing a real DraftData instance.
     * This ensures the JSON format is correct for the deserializer.
     */
    private fun completeJson(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            itemRequirements = mapOf("minecraft:iron_ingot" to 64),
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here and wait"))
        )
    )

    private fun missingName(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingDescription(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingDifficulty(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingCategory(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingAuthor(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingVersionRange(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            categoryData = mapOf("howToUse" to CategoryValue.TextValue("Stand here"))
        )
    )

    private fun missingCategoryData(): String = json.encodeToString(
        DraftData.serializer(),
        DraftData(
            name = "Iron Farm",
            description = "A villager-based iron farm that produces 300 iron/hour.",
            difficulty = IdeaDifficulty.MID_GAME,
            category = IdeaCategory.FARM,
            author = Author.SingleAuthor("Steve"),
            versionRange = MinecraftVersionRange.Unbounded,
            categoryData = null
        )
    )

    @Test
    fun `complete valid draft deserializes to CreateIdeaInput`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(completeJson()))

        assertIs<Result.Success<*>>(result)
        val input = (result as Result.Success).value
        assertTrue(input.name == "Iron Farm")
        assertTrue(input.description.isNotBlank())
    }

    @Test
    fun `missing name returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingName()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "name" })
    }

    @Test
    fun `missing description returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingDescription()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "description" })
    }

    @Test
    fun `missing difficulty returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingDifficulty()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "difficulty" })
    }

    @Test
    fun `missing category returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingCategory()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "category" })
    }

    @Test
    fun `missing author returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingAuthor()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "author" })
    }

    @Test
    fun `missing versionRange returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingVersionRange()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "versionRange" })
    }

    @Test
    fun `missing categoryData with category set returns validation error`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft(missingCategoryData()))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "categoryData" })
    }

    @Test
    fun `all missing fields returns multiple errors`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft("{}"))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        // name, description, difficulty, category, author, versionRange all missing
        assertTrue(error.errors.size >= 6)
    }

    @Test
    fun `empty draft data returns all required field errors`() = runBlocking {
        val result = DeserializeDraftStep.process(makeDraft("{}"))
        assertIs<Result.Failure<*>>(result)
        val error = (result as Result.Failure).error
        assertIs<AppFailure.ValidationError>(error)
        val fieldNames = error.errors.filterIsInstance<ValidationFailure.MissingParameter>().map { it.parameterName }
        assertContains(fieldNames, "name")
        assertContains(fieldNames, "description")
        assertContains(fieldNames, "difficulty")
        assertContains(fieldNames, "category")
        assertContains(fieldNames, "author")
        assertContains(fieldNames, "versionRange")
    }
}
