package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.PerformanceTestData
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.pipeline.idea.createsession.getWizardSession
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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
    val categoryData: Map<String, CategoryValue>,
)

/**
 * Handles the creation of a new idea.
 * Validates input, creates idea in database, and returns HTML fragment.
 */
suspend fun ApplicationCall.handleCreateIdea() {
    val user = getUser()
    val data = getWizardSession()

    executePipeline(
        onSuccess = {
            clientRedirect("/app/ideas/$it")
        }
    ) {
        value(data)
            .step(ValidateIdeaInputStep)
            .step(CreateIdeaStep(user.id))
    }
}

object ValidateIdeaInputStep : Step<CreateIdeaWizardSession, AppFailure.ValidationError, CreateIdeaInput> {
    override suspend fun process(input: CreateIdeaWizardSession): Result<AppFailure.ValidationError, CreateIdeaInput> {
        val errors = mutableListOf<ValidationFailure>()

        val name = input.name?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("name"))
        val description = input.description?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("description"))
        val difficulty = input.difficulty?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("difficulty"))
        val category = input.category?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("category"))
        val author = input.author?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("author"))
        val versionRange = input.versionRange?.let { Result.success(it) } ?: Result.failure(ValidationFailure.MissingParameter("versionRange"))
        val itemRequirements = input.itemRequirements ?: emptyMap()

        var categoryData = mapOf<String, CategoryValue>()

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
            errors.add(versionRange.error)
        }
        if (category is Result.Failure) {
            errors.add(category.error)
        } else if (category is Result.Success) {
            val categoryDataResult = input.categoryData
            if (categoryDataResult == null || categoryDataResult.isEmpty()) {
                errors.add(ValidationFailure.MissingParameter("categoryData"))
            } else {
                categoryData = input.categoryData
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
                itemRequirements = itemRequirements,
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
                        val authorJson = Json.encodeToString(Author.serializer(), input.author)
                        val versionRangeJson = Json.encodeToString(MinecraftVersionRange.serializer(), input.versionRange)
                        val categoryDataJson = Json.encodeToString(MapSerializer(String.serializer(), CategoryValue.serializer()), input.categoryData)

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
                                val subAuthorsArray = statement.connection.createArrayOf("jsonb", emptyArray())
                                statement.setArray(5, subAuthorsArray)

                                // Convert labels to PostgreSQL array
                                val labelsArray = statement.connection.createArrayOf("text", emptyArray())
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
}

