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
import app.mcorg.pipeline.idea.commonsteps.IdeaProductionModeInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One mode as the draft holds it — named, with its item -> rate map. */
@Serializable
data class DraftProductionMode(
    val name: String = "",
    val rates: Map<String, Int?> = emptyMap(),
)

@Serializable
data class DraftData(
    val name: String? = null,
    val description: String? = null,
    val difficulty: IdeaDifficulty? = null,
    val category: IdeaCategory? = null,
    val author: Author? = null,
    val versionRange: MinecraftVersionRange? = null,
    val itemRequirements: Map<String, Int>? = null,
    /** What the idea produces, by mode (MCO-412). Absent for anything that produces nothing. */
    val productionModes: List<DraftProductionMode>? = null,
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

/**
 * A draft nobody has typed anything into yet. Opening the create flow mints one of these, so
 * without this check a few aborted visits leave a list of identical "Untitled Draft" rows.
 */
val IdeaDraft.isUntouched: Boolean
    get() = data.isBlank() || data.trim() == "{}"

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

        // What a *private* design must have: a name, a kind, and a difficulty. Nothing else.
        // A personal note is allowed to be a name and a category — quality bars belong to
        // publishing to the hub, which is a separate step, not to storing your own design.
        if (data.name.isNullOrBlank()) errors.add(ValidationFailure.MissingParameter("name"))
        if (data.difficulty == null) errors.add(ValidationFailure.MissingParameter("difficulty"))
        if (data.category == null) errors.add(ValidationFailure.MissingParameter("category"))
        if (data.author == null) errors.add(ValidationFailure.MissingParameter("author"))
        if (data.versionRange == null) errors.add(ValidationFailure.MissingParameter("versionRange"))

        // Not required: after the MCO-204 slimming almost every category field is optional, so
        // demanding a non-empty block meant a minimal idea could never be saved.
        val categoryData = data.categoryData

        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors))
        }

        return Result.success(
            CreateIdeaInput(
                name = data.name!!,
                description = data.description.orEmpty(),
                category = data.category!!,
                difficulty = data.difficulty!!,
                labels = emptyList(),
                author = data.author!!,
                subAuthors = emptyList(),
                versionRange = data.versionRange!!,
                testData = null,
                itemRequirements = data.itemRequirements ?: emptyMap(),
                productionModes = data.productionModes.orEmpty()
                    .map { IdeaProductionModeInput(name = it.name, rates = it.rates) },
                categoryData = categoryData ?: emptyMap()
            )
        )
    }
}
