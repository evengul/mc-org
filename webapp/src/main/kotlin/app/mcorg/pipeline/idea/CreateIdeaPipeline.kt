package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.PerformanceTestData
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.validators.*
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class CreateIdeaInput(
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val difficulty: IdeaDifficulty,
    val labels: List<String>,
    val author: Author,
    val subAuthors: List<Author>,
    val versionRange: MinecraftVersionRange,
    val testData: PerformanceTestData?,
    val itemRequirements: Map<String, Int>,
    val categoryData: Map<String, Any>,
)

/**
 * Handles the creation of a new idea.
 * Validates input, creates idea in database, and returns HTML fragment.
 */
suspend fun ApplicationCall.handleCreateIdea() {
    val user = getUser()
    val parameters = receiveParameters()

    executePipeline(
        onSuccess = {
            clientRedirect("/app/ideas/$it")
        }
    ) {
        value(parameters)
            .step(ValidateIdeaInputStep)
            .step(CreateIdeaStep(user.id))
    }
}

object ValidateIdeaInputStep : Step<Parameters, AppFailure.ValidationError, CreateIdeaInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateIdeaInput> {
        val errors = mutableListOf<ValidationFailure>()

        val name = ValidateIdeaNameStep.process(input)
        val description = ValidateIdeaDescriptionStep.process(input)
        val difficulty = ValidateIdeaDifficultyStep.process(input)
        val category = ValidateIdeaCategoryStep.process(input)
        val author = ValidateIdeaAuthorStep.process(input)
        val versionRange = ValidateIdeaMinecraftVersionStep.process(input)
        val itemRequirements = ValidateAllItemRequirementsStep(versionRange.getOrNull() ?: MinecraftVersionRange.Unbounded).process(input)

        var categoryData = mapOf<String, Any>()

        if (name is Result.Failure) {
            errors.add(name.error)
        }
        if (description is Result.Failure) {
            errors.add(description.error)
        }
        if (difficulty is Result.Failure) {
            errors.add(difficulty.error)
        }
        if (category is Result.Failure) {
            errors.add(category.error)
        }
        if (author is Result.Failure) {
            errors.add(author.error)
        }
        if (versionRange is Result.Failure) {
            errors.addAll(versionRange.error)
        }
        if (itemRequirements is Result.Failure) {
            errors.addAll(itemRequirements.error)
        }
        if (category is Result.Failure) {
            errors.add(category.error)
        } else if (category is Result.Success) {
            val categoryDataResult = ValidateIdeaCategoryDataStep(category.value).process(input)
            if (categoryDataResult is Result.Failure) {
                errors.addAll(categoryDataResult.error)
            } else {
                categoryData = categoryDataResult.getOrNull()!!
            }
        }

        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors))
        }

        return Result.success(
            CreateIdeaInput(
                name = name.getOrNull()!!,
                description = description.getOrNull()!!,
                category = category.getOrNull()!!,
                difficulty = difficulty.getOrNull()!!,
                labels = emptyList(),
                author = author.getOrNull()!!,
                subAuthors = emptyList(),
                versionRange = versionRange.getOrNull()!!,
                testData = null,
                itemRequirements = itemRequirements.getOrNull()?.mapKeys { it.key.id } ?: emptyMap(),
                categoryData = categoryData
            )
        )
    }
}

data class CreateIdeaStep(val userId: Int) : Step<CreateIdeaInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateIdeaInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction(
            step = { connection ->
                object : Step<CreateIdeaInput, AppFailure.DatabaseError, Int> {
                    override suspend fun process(input: CreateIdeaInput): Result<AppFailure.DatabaseError, Int> {
                        // Serialize complex fields to JSON
                        val authorJson = serializeAuthor(input.author)
                        val subAuthorsJson = input.subAuthors.map { serializeAuthor(it) }
                        val versionRangeJson = serializeVersionRange(input.versionRange)
                        val categoryDataJson = serializeCategoryData(input.categoryData)

                        // Insert idea
                        val ideaIdResult = DatabaseSteps.update<CreateIdeaInput>(
                            sql = SafeSQL.insert("""
                            INSERT INTO ideas (
                                name, description, category, author, sub_authors, labels,
                                difficulty, minecraft_version_range, category_data, created_by,
                                created_at, updated_at
                            )
                            VALUES (?, ?, ?, ?::jsonb, ?::jsonb[], ?, ?, ?::jsonb, ?::jsonb, ?, NOW(), NOW())
                            RETURNING id
                        """),
                            parameterSetter = { statement, _ ->
                                statement.setString(1, input.name)
                                statement.setString(2, input.description)
                                statement.setString(3, input.category.name)
                                statement.setString(4, authorJson)

                                // Convert array to PostgreSQL array format
                                val subAuthorsArray = statement.connection.createArrayOf("jsonb", subAuthorsJson.toTypedArray())
                                statement.setArray(5, subAuthorsArray)

                                // Convert labels to PostgreSQL array
                                val labelsArray = statement.connection.createArrayOf("text", input.labels.toTypedArray())
                                statement.setArray(6, labelsArray)

                                statement.setString(7, input.difficulty.name)
                                statement.setString(8, versionRangeJson)
                                statement.setString(9, categoryDataJson)
                                statement.setInt(10, userId)
                            },
                            connection
                        ).process(input)

                        if (ideaIdResult is Result.Failure) {
                            return ideaIdResult
                        }

                        val ideaId = ideaIdResult.getOrNull()!!

                        // Insert test data if provided
                        if (input.testData != null) {
                            val testDataResult = DatabaseSteps.update<PerformanceTestData>(
                                sql = SafeSQL.insert("""
                                INSERT INTO idea_test_data (idea_id, mspt, hardware, minecraft_version, created_at)
                                VALUES (?, ?, ?, ?, NOW())
                            """),
                                parameterSetter = { statement, _ ->
                                    statement.setInt(1, ideaId)
                                    statement.setDouble(2, input.testData.mspt)
                                    statement.setString(3, input.testData.hardware)
                                    statement.setString(4, input.testData.version.toString())
                                },
                                connection
                            ).process(input.testData)

                            if (testDataResult is Result.Failure) {
                                return testDataResult
                            }
                        }

                        if (input.itemRequirements.isNotEmpty()) {
                            val requirementResult = DatabaseSteps.batchUpdate<Pair<String, Int>>(
                                SafeSQL.insert("""
                                    INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)
                                """.trimIndent()),
                                parameterSetter = { statement, (itemId, quantity) ->
                                    statement.setInt(1, ideaId)
                                    statement.setString(2, itemId)
                                    statement.setInt(3, quantity)
                                },
                                chunkSize = 500,
                                transactionConnection = connection
                            ).process(input.itemRequirements.entries.map { it.key to it.value })

                            if (requirementResult is Result.Failure) {
                                return requirementResult
                            }
                        }

                        return Result.success(ideaId)
                    }
                }
            }
        ).process(input)
    }

    private fun serializeAuthor(author: Author): String {
        return when (author) {
            is Author.SingleAuthor -> Json.encodeToString(
                buildJsonObject {
                    put("type", "single")
                    put("name", author.name)
                }
            )
            is Author.Team -> Json.encodeToString(
                buildJsonObject {
                    put("type", "team")
                    putJsonArray("members") {
                        author.members.forEach { member ->
                            addJsonObject {
                                put("name", member.name)
                                put("index", member.order)
                                put("title", member.role)
                                putJsonArray("responsibleFor") {
                                    member.contributions.forEach { add(it) }
                                }
                            }
                        }
                    }
                }
            )
            is Author.TeamAuthor -> Json.encodeToString(
                buildJsonObject {
                    put("type", "single")
                    put("name", author.name)
                }
            )
        }
    }

    private fun serializeVersionRange(range: MinecraftVersionRange): String {
        return when (range) {
            is MinecraftVersionRange.Bounded -> Json.encodeToString(
                buildJsonObject {
                    put("type", "bounded")
                    put("from", range.from.toString())
                    put("to", range.to.toString())
                }
            )
            is MinecraftVersionRange.LowerBounded -> Json.encodeToString(
                buildJsonObject {
                    put("type", "lowerBounded")
                    put("from", range.from.toString())
                }
            )
            is MinecraftVersionRange.UpperBounded -> Json.encodeToString(
                buildJsonObject {
                    put("type", "upperBounded")
                    put("to", range.to.toString())
                }
            )
            MinecraftVersionRange.Unbounded -> Json.encodeToString(
                buildJsonObject {
                    put("type", "unbounded")
                }
            )
        }
    }

    private fun serializeCategoryData(data: Map<String, Any>): String {
        return buildJsonObject {
            data.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    is List<*> -> {
                        putJsonArray(key) {
                            value.forEach { item ->
                                when (item) {
                                    is String -> add(item)
                                    is Int -> add(item)
                                    is Double -> add(item)
                                    is Boolean -> add(item)
                                    else -> add(item.toString())
                                }
                            }
                        }
                    }
                    is Map<*, *> -> {
                        putJsonObject(key) {
                            @Suppress("UNCHECKED_CAST")
                            val nestedMap = value as Map<String, Any>
                            nestedMap.forEach { (nestedKey, nestedValue) ->
                                when (nestedValue) {
                                    is String -> put(nestedKey, nestedValue)
                                    is Int -> put(nestedKey, nestedValue)
                                    is Double -> put(nestedKey, nestedValue)
                                    is Boolean -> put(nestedKey, nestedValue)
                                    else -> put(nestedKey, nestedValue.toString())
                                }
                            }
                        }
                    }
                    else -> put(key, value.toString())
                }
            }
        }.toString()
    }
}

