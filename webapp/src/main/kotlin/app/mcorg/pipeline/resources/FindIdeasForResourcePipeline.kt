package app.mcorg.pipeline.resources

import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.foundIdeas
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

suspend fun ApplicationCall.handleFindIdeasForResource() {
    val worldId = this.getWorldId()
    val gatheringId = this.getResourceGatheringId()

    executePipeline(
        onSuccess = { respondHtml(createHTML().div {
            foundIdeas(worldId, gatheringId to it.first, it.second)
        })}
    ) {
        value(gatheringId)
            .step(ValidateGathering)
            .step(FindIdeasStep(worldId)) // TODO: Add existing projects as returned values
            .step(FilterIdeasByWorldVersionStep(worldId))
    }
}

private object ValidateGathering : Step<Int, AppFailure, Pair<String, String>> {
    override suspend fun process(input: Int): Result<AppFailure, Pair<String, String>> {
        val validationResult = DatabaseSteps.query<Int, Pair<String, String>?>(
            sql = SafeSQL.select("SELECT name, item_id FROM resource_gathering WHERE id = ?"),
            parameterSetter = { statement, resourceGatheringId -> statement.setInt(1, resourceGatheringId) },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getString("name") to resultSet.getString("item_id")
                } else {
                    null
                }
            }
        ).process(input)

        if (validationResult is Result.Failure) {
            return validationResult
        }

        return if (validationResult.getOrNull() != null) {
            Result.success(validationResult.getOrNull()!!)
        } else {
            Result.failure(AppFailure.ValidationError(listOf(
                ValidationFailure.InvalidFormat("resourceGatheringId", "Resource Gathering")
            )))
        }
    }
}

data class FoundIdea(
    val id: Int,
    val name: String,
    val rate: Int,
    val versionRange: MinecraftVersionRange,
    val alreadyImported: Boolean,
)

private data class FindIdeasStep(val worldId: Int) : Step<Pair<String, String>, AppFailure, Pair<String, List<FoundIdea>>> {
    override suspend fun process(input: Pair<String, String>): Result<AppFailure, Pair<String, List<FoundIdea>>> {
        return DatabaseSteps.query<String, Pair<String, List<FoundIdea>>>(
            sql = SafeSQL.select("""
                SELECT ideas.id, ideas.name, minecraft_version_range, category_data ->> 'productionRate' as production_rate, projects.id IS NOT NULL AS already_imported
                FROM ideas
                         LEFT JOIN projects ON ideas.id = projects.project_idea_id
                WHERE category = 'FARM'
                  AND category_data -> 'productionRate' IS NOT NULL
                  AND category_data -> 'productionRate' ->> 'value' LIKE ?
            """.trimIndent()),
            parameterSetter = { statement, itemId -> statement.setString(1, "%$itemId%") },
            resultMapper = { resultSet ->
                input.first to buildList {
                    while (resultSet.next()) {
                        val id = resultSet.getInt("id")
                        val name = resultSet.getString("name")
                        val productionRateJson = resultSet.getString("production_rate")?.let {
                            Json.decodeFromString(CategoryValue.serializer(), it) as? CategoryValue.MapValue
                        }?.value ?: emptyMap()

                        val totalRate = productionRateJson.values.sumOf {
                            when (it) {
                                is CategoryValue.MapValue -> {
                                    when (val rate = it.value[input.second]) {
                                        is CategoryValue.IntValue -> rate.value
                                        else -> 0
                                    }
                                }
                                else -> 0
                            }
                        }
                        val alreadyImported = resultSet.getBoolean("already_imported")
                        val versionRange = Json.decodeFromString(MinecraftVersionRange.serializer(), resultSet.getString("minecraft_version_range"))
                        if (totalRate > 0) {
                            add(FoundIdea(id = id, name = name, rate = totalRate, versionRange, alreadyImported))
                        }
                    }
                }
            }
        ).process(input.second)
    }
}

private data class FilterIdeasByWorldVersionStep(val worldId: Int) : Step<Pair<String, List<FoundIdea>>, AppFailure, Pair<String, List<FoundIdea>>> {
    override suspend fun process(input: Pair<String, List<FoundIdea>>): Result<AppFailure, Pair<String, List<FoundIdea>>> {
        val worldVersionResult = DatabaseSteps.query<Int, MinecraftVersion>(
            sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
            parameterSetter = { statement, worldId -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                resultSet.next()
                MinecraftVersion.fromString(resultSet.getString("version"))
            }
        ).process(worldId)

        if (worldVersionResult is Result.Failure) {
            return worldVersionResult
        }

        val filteredIdeas = input.second.filter { idea ->
            idea.versionRange.contains(worldVersionResult.getOrNull()!!)
        }

        return Result.success(input.first to filteredIdeas)
    }
}