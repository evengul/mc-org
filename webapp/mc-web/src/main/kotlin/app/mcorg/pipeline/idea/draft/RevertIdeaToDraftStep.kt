package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

data class RevertIdeaToDraftInput(val ideaId: Int, val userId: Int)

private data class RawIdeaExtras(val categoryDataJson: String, val itemRequirementsJson: String)

class RevertIdeaToDraftStep : Step<RevertIdeaToDraftInput, AppFailure, Int> {
    override suspend fun process(input: RevertIdeaToDraftInput): Result<AppFailure, Int> {
        // Fetch idea (still active at this point)
        val idea = when (val r = GetIdeaStep.process(input.ideaId)) {
            is Result.Failure -> return Result.failure(r.error)
            is Result.Success -> r.value
        }

        // Query raw JSON blobs directly from DB to avoid polymorphic serialization roundtrip
        val extras = DatabaseSteps.query<Int, RawIdeaExtras>(
            sql = SafeSQL.select(
                """
                SELECT
                    i.category_data::text AS category_data,
                    COALESCE(
                        (SELECT json_object_agg(item_id, quantity)
                         FROM idea_item_requirements WHERE idea_id = i.id),
                        '{}'
                    )::text AS item_requirements
                FROM ideas i
                WHERE i.id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                if (rs.next()) RawIdeaExtras(
                    categoryDataJson = rs.getString("category_data") ?: "{}",
                    itemRequirementsJson = rs.getString("item_requirements") ?: "{}"
                ) else RawIdeaExtras("{}", "{}")
            }
        ).process(input.ideaId).getOrNull() ?: RawIdeaExtras("{}", "{}")

        // Build draft JSON — categoryData and itemRequirements are copied verbatim from DB
        val draftData = buildJsonObject {
            put("name", idea.name)
            put("description", idea.description)
            put("difficulty", idea.difficulty.name)
            put("category", idea.category.name)
            put("author", Json.encodeToJsonElement(Author.serializer(), idea.author))
            put("versionRange", Json.encodeToJsonElement(MinecraftVersionRange.serializer(), idea.worksInVersionRange))
            if (extras.categoryDataJson != "{}") {
                put("categoryData", Json.parseToJsonElement(extras.categoryDataJson))
            }
            if (extras.itemRequirementsJson != "{}") {
                put("itemRequirements", Json.parseToJsonElement(extras.itemRequirementsJson))
            }
        }.toString()

        // Create pre-populated draft pointing back to the source idea
        val draftId = when (val r = CreateDraftStep(input.userId, input.ideaId, draftData).process(Unit)) {
            is Result.Failure -> return Result.failure(r.error)
            is Result.Success -> r.value
        }

        // Mark idea as inactive while it is being edited
        val deactivateResult = DatabaseSteps.update<Int>(
            sql = SafeSQL.update("UPDATE ideas SET is_active = FALSE WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(input.ideaId)

        if (deactivateResult is Result.Failure) {
            return Result.failure(deactivateResult.error)
        }

        return Result.success(draftId)
    }
}
