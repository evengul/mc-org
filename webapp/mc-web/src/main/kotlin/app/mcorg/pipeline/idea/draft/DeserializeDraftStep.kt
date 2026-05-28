package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.CreateIdeaInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class DraftData(
    val name: String? = null,
    val description: String? = null,
    val difficulty: IdeaDifficulty? = null,
    val category: IdeaCategory? = null,
    val author: Author? = null,
    val versionRange: MinecraftVersionRange? = null,
    val itemRequirements: Map<String, Int>? = null,
    val categoryData: Map<String, CategoryValue>? = null
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Extracts the draft name from its JSONB data using proper deserialization.
 * Returns null if data is malformed or the name field is absent.
 */
val IdeaDraft.name: String?
    get() = try {
        json.decodeFromString(DraftData.serializer(), data).name
    } catch (_: Exception) {
        null
    }

object DeserializeDraftStep : Step<IdeaDraft, AppFailure.ValidationError, CreateIdeaInput> {
    override suspend fun process(input: IdeaDraft): Result<AppFailure.ValidationError, CreateIdeaInput> {
        val data = try {
            json.decodeFromString(DraftData.serializer(), input.data)
        } catch (e: Exception) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.CustomValidation("data", "Draft data could not be read: ${e.message}"))
                )
            )
        }

        val errors = mutableListOf<ValidationFailure>()

        if (data.name.isNullOrBlank()) errors.add(ValidationFailure.MissingParameter("name"))
        if (data.description.isNullOrBlank()) errors.add(ValidationFailure.MissingParameter("description"))
        if (data.difficulty == null) errors.add(ValidationFailure.MissingParameter("difficulty"))
        if (data.category == null) errors.add(ValidationFailure.MissingParameter("category"))
        if (data.author == null) errors.add(ValidationFailure.MissingParameter("author"))
        if (data.versionRange == null) errors.add(ValidationFailure.MissingParameter("versionRange"))

        val categoryData = data.categoryData
        if (data.category != null && (categoryData == null || categoryData.isEmpty())) {
            errors.add(ValidationFailure.MissingParameter("categoryData"))
        }

        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors))
        }

        return Result.success(
            CreateIdeaInput(
                name = data.name!!,
                description = data.description!!,
                category = data.category!!,
                difficulty = data.difficulty!!,
                labels = emptyList(),
                author = data.author!!,
                subAuthors = emptyList(),
                versionRange = data.versionRange!!,
                testData = null,
                itemRequirements = data.itemRequirements ?: emptyMap(),
                categoryData = categoryData ?: emptyMap()
            )
        )
    }
}
