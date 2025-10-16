package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.PerformanceTestData
import app.mcorg.domain.model.idea.RatingSummary
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Input data for creating an idea
 */
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
    val categoryData: Map<String, Any>,
    val createdBy: Int
)

/**
 * Failure types for idea creation
 */
sealed class CreateIdeaFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateIdeaFailures()
    object InsufficientPermissions : CreateIdeaFailures()
    object DatabaseError : CreateIdeaFailures()
}

/**
 * Step to validate and parse idea input from form parameters
 */
object ValidateIdeaInputStep : Step<Parameters, CreateIdeaFailures, CreateIdeaInput> {
    override suspend fun process(input: Parameters): Result<CreateIdeaFailures, CreateIdeaInput> {
        val errors = mutableListOf<ValidationFailure>()

        // Validate basic fields
        val name = input["name"]?.trim()
        if (name.isNullOrBlank()) {
            errors.add(ValidationFailure.MissingParameter("name"))
        } else if (name.length < 3 || name.length > 255) {
            errors.add(ValidationFailure.InvalidLength("name", 3, 255))
        }

        val description = input["description"]?.trim()
        if (description.isNullOrBlank()) {
            errors.add(ValidationFailure.MissingParameter("description"))
        } else if (description.length < 20 || description.length > 5000) {
            errors.add(ValidationFailure.InvalidLength("description", 20, 5000))
        }

        // Validate category
        val categoryStr = input["category"]
        val category = try {
            if (categoryStr.isNullOrBlank()) {
                errors.add(ValidationFailure.MissingParameter("category"))
                null
            } else {
                IdeaCategory.valueOf(categoryStr.uppercase())
            }
        } catch (_: IllegalArgumentException) {
            errors.add(ValidationFailure.InvalidFormat("category", "Invalid category"))
            null
        }

        // Validate difficulty
        val difficultyStr = input["difficulty"]
        val difficulty = try {
            if (difficultyStr.isNullOrBlank()) {
                errors.add(ValidationFailure.MissingParameter("difficulty"))
                null
            } else {
                IdeaDifficulty.valueOf(difficultyStr.uppercase())
            }
        } catch (_: IllegalArgumentException) {
            errors.add(ValidationFailure.InvalidFormat("difficulty", "Invalid difficulty"))
            null
        }

        // Parse labels (comma-separated)
        val labels = input["labels"]?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        // Parse author
        val author = parseAuthor(input, errors)

        // Parse sub-authors (contributors)
        val subAuthors = parseSubAuthors(input)

        // Parse version range
        val versionRange = parseVersionRange(input, errors)

        // Parse optional test data
        val testData = parseTestData(input)

        // Parse category-specific data
        val categoryData = parseCategoryData(input)

        if (errors.isNotEmpty()) {
            return Result.failure(CreateIdeaFailures.ValidationError(errors))
        }

        return Result.success(
            CreateIdeaInput(
                name = name!!,
                description = description!!,
                category = category!!,
                difficulty = difficulty!!,
                labels = labels,
                author = author!!,
                subAuthors = subAuthors,
                versionRange = versionRange!!,
                testData = testData,
                categoryData = categoryData,
                createdBy = 0 // Will be injected by InjectIdeaContextStep
            )
        )
    }

    private fun parseAuthor(params: Parameters, errors: MutableList<ValidationFailure>): Author? {
        val authorType = params["authorType"] ?: "single"

        return when (authorType) {
            "single" -> {
                val authorName = params["authorName"]?.trim()
                if (authorName.isNullOrBlank()) {
                    errors.add(ValidationFailure.MissingParameter("authorName"))
                    null
                } else {
                    Author.SingleAuthor(authorName)
                }
            }
            "team" -> {
                val members = mutableListOf<Author.TeamAuthor>()
                var index = 0

                while (params["teamMembers[$index][name]"] != null) {
                    val name = params["teamMembers[$index][name]"]?.trim()
                    val title = params["teamMembers[$index][role]"]?.trim() ?: ""
                    val responsibleFor = params["teamMembers[$index][contributions]"]?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()

                    if (!name.isNullOrBlank()) {
                        members.add(Author.TeamAuthor(name, index, title, responsibleFor))
                    }
                    index++
                }

                if (members.isEmpty()) {
                    errors.add(ValidationFailure.MissingParameter("teamMembers"))
                    null
                } else {
                    Author.Team(members)
                }
            }
            else -> {
                errors.add(ValidationFailure.InvalidFormat("authorType", "Invalid author type"))
                null
            }
        }
    }

    private fun parseSubAuthors(params: Parameters): List<Author> {
        val subAuthorsStr = params["subAuthors"]?.trim()
        if (subAuthorsStr.isNullOrBlank()) return emptyList()

        return subAuthorsStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Author.SingleAuthor(it) }
    }

    private fun parseVersionRange(params: Parameters, errors: MutableList<ValidationFailure>): MinecraftVersionRange? {
        val rangeType = params["versionRangeType"] ?: "lowerBounded"

        return try {
            when (rangeType) {
                "bounded" -> {
                    val fromStr = params["versionFrom"]
                    val toStr = params["versionTo"]
                    if (fromStr.isNullOrBlank() || toStr.isNullOrBlank()) {
                        errors.add(ValidationFailure.MissingParameter("versionFrom/versionTo"))
                        null
                    } else {
                        MinecraftVersionRange.Bounded(
                            MinecraftVersion.fromString(fromStr),
                            MinecraftVersion.fromString(toStr)
                        )
                    }
                }
                "lowerBounded" -> {
                    val fromStr = params["versionFrom"]
                    if (fromStr.isNullOrBlank()) {
                        errors.add(ValidationFailure.MissingParameter("versionFrom"))
                        null
                    } else {
                        MinecraftVersionRange.LowerBounded(MinecraftVersion.fromString(fromStr))
                    }
                }
                "upperBounded" -> {
                    val toStr = params["versionTo"]
                    if (toStr.isNullOrBlank()) {
                        errors.add(ValidationFailure.MissingParameter("versionTo"))
                        null
                    } else {
                        MinecraftVersionRange.UpperBounded(MinecraftVersion.fromString(toStr))
                    }
                }
                "unbounded" -> MinecraftVersionRange.Unbounded
                else -> {
                    errors.add(ValidationFailure.InvalidFormat("versionRangeType", "Invalid version range type"))
                    null
                }
            }
        } catch (_: Exception) {
            errors.add(ValidationFailure.InvalidFormat("version", "Invalid version format"))
            null
        }
    }

    private fun parseTestData(params: Parameters): PerformanceTestData? {
        val msptStr = params["testMspt"]
        val hardware = params["testHardware"]?.trim()
        val versionStr = params["testVersion"]

        // All three are required if any is provided
        if (msptStr.isNullOrBlank() || hardware.isNullOrBlank() || versionStr.isNullOrBlank()) {
            return null
        }

        return try {
            val mspt = msptStr.toDouble()
            val version = MinecraftVersion.fromString(versionStr)
            PerformanceTestData(mspt, hardware, version)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCategoryData(params: Parameters): Map<String, Any> {
        val categoryData = mutableMapOf<String, Any>()

        // Parse all parameters starting with "categoryData["
        params.entries().forEach { (key, values) ->
            if (key.startsWith("categoryData[") && key.endsWith("]")) {
                val fieldKey = key.removePrefix("categoryData[").removeSuffix("]")

                // Handle nested fields like categoryData[size][x]
                if (fieldKey.contains("][")) {
                    val parts = fieldKey.split("][")
                    val mainKey = parts[0]
                    val subKey = parts[1]

                    @Suppress("UNCHECKED_CAST")
                    val nestedMap = categoryData.getOrPut(mainKey) { mutableMapOf<String, Any>() } as MutableMap<String, Any>

                    // Try to parse as number, otherwise keep as string
                    val value = values.firstOrNull()?.let { parseValue(it) }
                    if (value != null) {
                        nestedMap[subKey] = value
                    }
                } else if (key.endsWith("[]")) {
                    // Array field (checkboxes)
                    val actualKey = fieldKey.removeSuffix("[]")
                    categoryData[actualKey] = values.filter { it.isNotBlank() }
                } else {
                    // Simple field
                    val value = values.firstOrNull()?.let { parseValue(it) }
                    if (value != null) {
                        categoryData[fieldKey] = value
                    }
                }
            }
        }

        return categoryData
    }

    private fun parseValue(str: String): Any? {
        if (str.isBlank()) return null

        // Try boolean
        if (str.equals("true", ignoreCase = true)) return true
        if (str.equals("false", ignoreCase = true)) return false

        // Try number
        str.toIntOrNull()?.let { return it }
        str.toDoubleOrNull()?.let { return it }

        // Keep as string
        return str
    }
}

/**
 * Step to inject creator ID into the input
 */
class InjectIdeaContextStep(
    private val creatorId: Int
) : Step<CreateIdeaInput, CreateIdeaFailures, CreateIdeaInput> {
    override suspend fun process(input: CreateIdeaInput): Result<CreateIdeaFailures, CreateIdeaInput> {
        return Result.success(input.copy(createdBy = creatorId))
    }
}

/**
 * Step to create the idea in the database
 */
object CreateIdeaStep : Step<CreateIdeaInput, CreateIdeaFailures, Int> {
    override suspend fun process(input: CreateIdeaInput): Result<CreateIdeaFailures, Int> {
        return DatabaseSteps.transaction(
            step = object : Step<CreateIdeaInput, CreateIdeaFailures, Int> {
                override suspend fun process(input: CreateIdeaInput): Result<CreateIdeaFailures, Int> {
                    // Serialize complex fields to JSON
                    val authorJson = serializeAuthor(input.author)
                    val subAuthorsJson = input.subAuthors.map { serializeAuthor(it) }
                    val versionRangeJson = serializeVersionRange(input.versionRange)
                    val categoryDataJson = serializeCategoryData(input.categoryData)

                    // Insert idea
                    val ideaIdResult = DatabaseSteps.update<CreateIdeaInput, CreateIdeaFailures>(
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
                            statement.setInt(10, input.createdBy)
                        },
                        errorMapper = { CreateIdeaFailures.DatabaseError }
                    ).process(input)

                    if (ideaIdResult is Result.Failure) {
                        return ideaIdResult
                    }

                    val ideaId = ideaIdResult.getOrNull()!!

                    // Insert test data if provided
                    if (input.testData != null) {
                        val testDataResult = DatabaseSteps.update<PerformanceTestData, CreateIdeaFailures>(
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
                            errorMapper = { CreateIdeaFailures.DatabaseError }
                        ).process(input.testData)

                        if (testDataResult is Result.Failure) {
                            return testDataResult
                        }
                    }

                    return Result.success(ideaId)
                }
            },
            errorMapper = { CreateIdeaFailures.DatabaseError }
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

/**
 * Step to fetch the newly created idea from the database
 */
object GetCreatedIdeaStep : Step<Int, CreateIdeaFailures, Idea> {
    override suspend fun process(input: Int): Result<CreateIdeaFailures, Idea> {
        return DatabaseSteps.query<Int, CreateIdeaFailures, Idea>(
            sql = SafeSQL.select("""
                SELECT 
                    id, name, description, category, author, sub_authors, labels,
                    favourites_count, rating_average, rating_count, difficulty,
                    minecraft_version_range, category_data, created_by, created_at
                FROM ideas
                WHERE id = ?
            """),
            parameterSetter = { statement, ideaId ->
                statement.setInt(1, ideaId)
            },
            errorMapper = { CreateIdeaFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    Idea(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                        category = IdeaCategory.valueOf(rs.getString("category")),
                        author = deserializeAuthor(rs.getString("author")),
                        subAuthors = deserializeSubAuthors(rs.getArray("sub_authors")),
                        labels = (rs.getArray("labels").array as Array<*>).map { it.toString() },
                        favouritesCount = rs.getInt("favourites_count"),
                        rating = RatingSummary(
                            average = rs.getDouble("rating_average"),
                            total = rs.getInt("rating_count")
                        ),
                        difficulty = IdeaDifficulty.valueOf(rs.getString("difficulty")),
                        worksInVersionRange = deserializeVersionRange(rs.getString("minecraft_version_range")),
                        testData = emptyList(), // Test data loaded separately if needed
                        categoryData = deserializeCategoryData(rs.getString("category_data")),
                        createdBy = rs.getInt("created_by"),
                        createdAt = rs.getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault())
                    )
                } else {
                    throw IllegalStateException("Idea not found after creation")
                }
            }
        ).process(input)
    }

    private fun deserializeAuthor(json: String): Author {
        val obj = Json.parseToJsonElement(json).jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "single" -> Author.SingleAuthor(obj["name"]!!.jsonPrimitive.content)
            "team" -> {
                val members = obj["members"]!!.jsonArray.map { memberObj ->
                    val member = memberObj.jsonObject
                    Author.TeamAuthor(
                        name = member["name"]!!.jsonPrimitive.content,
                        order = member["order"]!!.jsonPrimitive.int,
                        role = member["role"]!!.jsonPrimitive.content,
                        contributions = member["contributions"]!!.jsonArray.map { it.jsonPrimitive.content }
                    )
                }
                Author.Team(members)
            }
            else -> Author.SingleAuthor("Unknown")
        }
    }

    private fun deserializeSubAuthors(array: java.sql.Array?): List<Author> {
        if (array == null) return emptyList()
        val jsonArray = array.array as Array<*>
        return jsonArray.map { deserializeAuthor(it.toString()) }
    }

    private fun deserializeVersionRange(json: String): MinecraftVersionRange {
        val obj = Json.parseToJsonElement(json).jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "bounded" -> MinecraftVersionRange.Bounded(
                MinecraftVersion.fromString(obj["from"]!!.jsonPrimitive.content),
                MinecraftVersion.fromString(obj["to"]!!.jsonPrimitive.content)
            )
            "lowerBounded" -> MinecraftVersionRange.LowerBounded(
                MinecraftVersion.fromString(obj["from"]!!.jsonPrimitive.content)
            )
            "upperBounded" -> MinecraftVersionRange.UpperBounded(
                MinecraftVersion.fromString(obj["to"]!!.jsonPrimitive.content)
            )
            "unbounded" -> MinecraftVersionRange.Unbounded
            else -> MinecraftVersionRange.Unbounded
        }
    }

    private fun deserializeCategoryData(json: String): Map<String, Any> {
        val jsonElement = Json.parseToJsonElement(json)
        if (jsonElement !is JsonObject) return emptyMap()

        return jsonElement.entries.associate { (key, value) ->
            key to jsonElementToAny(value)
        }
    }

    private fun jsonElementToAny(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.toIntOrNull() != null -> element.content.toInt()
                    element.content.toDoubleOrNull() != null -> element.content.toDouble()
                    else -> element.content
                }
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> element.entries.associate { (key, value) ->
                key to jsonElementToAny(value)
            }
            else -> element.toString()
        }
    }
}
