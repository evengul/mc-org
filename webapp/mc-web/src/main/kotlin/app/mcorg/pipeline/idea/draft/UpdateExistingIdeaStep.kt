package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.CreateIdeaInput
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class UpdateExistingIdeaInput(val ideaId: Int, val createInput: CreateIdeaInput)

class UpdateExistingIdeaStep : Step<UpdateExistingIdeaInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: UpdateExistingIdeaInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction(
            step = { connection ->
                object : Step<UpdateExistingIdeaInput, AppFailure.DatabaseError, Int> {
                    override suspend fun process(input: UpdateExistingIdeaInput): Result<AppFailure.DatabaseError, Int> {
                        val authorJson = Json.encodeToString(Author.serializer(), input.createInput.author)
                        val versionRangeJson = Json.encodeToString(MinecraftVersionRange.serializer(), input.createInput.versionRange)
                        val categoryDataJson = Json.encodeToString(
                            MapSerializer(String.serializer(), CategoryValue.serializer()),
                            input.createInput.categoryData
                        )

                        val updateResult = DatabaseSteps.update<UpdateExistingIdeaInput>(
                            sql = SafeSQL.update("""
                                UPDATE ideas SET
                                    name = ?, description = ?, category = ?, author = ?::jsonb,
                                    difficulty = ?, minecraft_version_range = ?::jsonb, category_data = ?::jsonb,
                                    is_active = TRUE, updated_at = NOW()
                                WHERE id = ?
                            """.trimIndent()),
                            parameterSetter = { stmt, _ ->
                                stmt.setString(1, input.createInput.name)
                                stmt.setString(2, input.createInput.description)
                                stmt.setString(3, input.createInput.category.name)
                                stmt.setString(4, authorJson)
                                stmt.setString(5, input.createInput.difficulty.name)
                                stmt.setString(6, versionRangeJson)
                                stmt.setString(7, categoryDataJson)
                                stmt.setInt(8, input.ideaId)
                            },
                            connection
                        ).process(input)

                        if (updateResult is Result.Failure) return updateResult

                        // Delete old item requirements
                        DatabaseSteps.update<Int>(
                            sql = SafeSQL.delete("DELETE FROM idea_item_requirements WHERE idea_id = ?"),
                            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
                            connection
                        ).process(input.ideaId)

                        // Insert new item requirements
                        if (input.createInput.itemRequirements.isNotEmpty()) {
                            val requirementResult = DatabaseSteps.batchUpdate<Pair<String, Int>>(
                                SafeSQL.insert("INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"),
                                parameterSetter = { stmt, (itemId, quantity) ->
                                    stmt.setInt(1, input.ideaId)
                                    stmt.setString(2, itemId)
                                    stmt.setInt(3, quantity)
                                },
                                chunkSize = 500,
                                transactionConnection = connection
                            ).process(input.createInput.itemRequirements.entries.map { it.key to it.value })

                            if (requirementResult is Result.Failure) return requirementResult
                        }

                        return Result.success(input.ideaId)
                    }
                }
            }
        ).process(input)
    }
}
