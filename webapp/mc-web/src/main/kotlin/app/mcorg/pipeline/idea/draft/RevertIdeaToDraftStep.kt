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

private data class RawIdeaExtras(
    val categoryDataJson: String,
    val itemRequirementsJson: String,
    val productionModesJson: String,
)

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
                        -- Base list only; build-time modes carry theirs alongside the mode
                        -- (MCO-463, V2_61_0), and json_object_agg over both would collapse four
                        -- variants onto one key per item.
                        (SELECT json_object_agg(item_id, quantity)
                         FROM idea_item_requirements WHERE idea_id = i.id AND mode_id IS NULL),
                        '{}'
                    )::text AS item_requirements,
                    COALESCE(
                        -- kind and requirements ride along for the same reason rates do: publishing
                        -- the draft replaces the idea wholesale, so a key missing here is deleted
                        -- rather than left alone. Reverting a four-variant farm to a draft and
                        -- saving it would otherwise flatten it back to one nameless mode.
                        (SELECT json_agg(
                                    json_build_object(
                                        'name', m.name,
                                        'kind', m.kind,
                                        'rates', COALESCE(m.rates, '{}'::json),
                                        'requirements', COALESCE(m.requirements, '{}'::json)
                                    )
                                    ORDER BY m.position, m.id
                                )
                         FROM (
                             SELECT pm.id, pm.name, pm.position, pm.kind,
                                    (SELECT json_object_agg(r.item_id, r.rate_per_hour)
                                     FROM idea_production_rates r WHERE r.mode_id = pm.id) AS rates,
                                    (SELECT json_object_agg(mr.item_id, mr.quantity)
                                     FROM idea_item_requirements mr WHERE mr.mode_id = pm.id) AS requirements
                             FROM idea_production_modes pm
                             WHERE pm.idea_id = i.id
                         ) m),
                        '[]'
                    )::text AS production_modes
                FROM ideas i
                WHERE i.id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                if (rs.next()) RawIdeaExtras(
                    categoryDataJson = rs.getString("category_data") ?: "{}",
                    itemRequirementsJson = rs.getString("item_requirements") ?: "{}",
                    productionModesJson = rs.getString("production_modes") ?: "[]"
                ) else RawIdeaExtras("{}", "{}", "[]")
            }
        ).process(input.ideaId).getOrNull() ?: RawIdeaExtras("{}", "{}", "[]")

        // Build draft JSON — categoryData, itemRequirements and productionModes are copied verbatim
        // from DB. Everything the idea holds has to come back, because publishing the draft replaces
        // the idea wholesale: replaceIdeaProductionModes opens with an unconditional DELETE, so a
        // key missing here is not "left alone", it is deleted on save. Editing a description used to
        // silently wipe every rate the farm had.
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
            if (extras.productionModesJson != "[]") {
                put("productionModes", Json.parseToJsonElement(extras.productionModesJson))
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
