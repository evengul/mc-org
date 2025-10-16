package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.*
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import kotlinx.serialization.json.*
import java.sql.Array
import java.time.ZoneId

/**
 * Step to get all ideas from the database
 */
object GetAllIdeasStep : Step<Unit, GetIdeasFailure, List<Idea>> {
    override suspend fun process(input: Unit): Result<GetIdeasFailure, List<Idea>> {
        return DatabaseSteps.query<Unit, GetIdeasFailure, List<Idea>>(
            sql = SafeSQL.select("""
                SELECT 
                    i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                    i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                    i.minecraft_version_range, i.category_data, i.created_by, i.created_at,
                    COALESCE(
                        json_agg(
                            json_build_object(
                                'mspt', t.mspt,
                                'hardware', t.hardware,
                                'version', t.minecraft_version
                            )
                        ) FILTER (WHERE t.id IS NOT NULL),
                        '[]'
                    ) as test_data
                FROM ideas i
                LEFT JOIN idea_test_data t ON i.id = t.idea_id
                GROUP BY i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                         i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                         i.minecraft_version_range, i.category_data, i.created_by, i.created_at
                ORDER BY i.created_at DESC
            """.trimIndent()),
            parameterSetter = { _, _ -> },
            errorMapper = { GetIdeasFailure.DatabaseError },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toIdea())
                    }
                }
            }
        ).process(input)
    }
}

/**
 * Step to get ideas filtered by category
 */
data class GetIdeasByCategoryInput(
    val category: IdeaCategory?
)

object GetIdeasByCategoryStep : Step<GetIdeasByCategoryInput, SearchIdeasFailure, List<Idea>> {
    override suspend fun process(input: GetIdeasByCategoryInput): Result<SearchIdeasFailure, List<Idea>> {
        return if (input.category == null) {
            GetAllIdeasStep.process(Unit)
                .mapError { SearchIdeasFailure.DatabaseError }
        } else {
            DatabaseSteps.query<GetIdeasByCategoryInput, SearchIdeasFailure, List<Idea>>(
                sql = SafeSQL.select("""
                    SELECT 
                        i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                        i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                        i.minecraft_version_range, i.category_data, i.created_by, i.created_at,
                        COALESCE(
                            json_agg(
                                json_build_object(
                                    'mspt', t.mspt,
                                    'hardware', t.hardware,
                                    'version', t.minecraft_version
                                )
                            ) FILTER (WHERE t.id IS NOT NULL),
                            '[]'
                        ) as test_data
                    FROM ideas i
                    LEFT JOIN idea_test_data t ON i.id = t.idea_id
                    WHERE i.category = ?
                    GROUP BY i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                             i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                             i.minecraft_version_range, i.category_data, i.created_by, i.created_at
                    ORDER BY i.created_at DESC
                """.trimIndent()),
                parameterSetter = { statement, categoryInput ->
                    statement.setString(1, categoryInput.category!!.name)
                },
                errorMapper = { SearchIdeasFailure.DatabaseError },
                resultMapper = { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toIdea())
                        }
                    }
                }
            ).process(input)
        }
    }
}

/**
 * Extension function to convert a ResultSet row to an Idea object
 */
private fun java.sql.ResultSet.toIdea(): Idea {
    return Idea(
        id = getInt("id"),
        name = getString("name"),
        description = getString("description"),
        category = IdeaCategory.valueOf(getString("category")),
        author = deserializeAuthor(getString("author")),
        subAuthors = deserializeSubAuthors(getArray("sub_authors")),
        labels = (getArray("labels").array as kotlin.Array<*>).mapNotNull { it?.toString() },
        favouritesCount = getInt("favourites_count"),
        rating = RatingSummary(
            average = getDouble("rating_average"),
            total = getInt("rating_count")
        ),
        difficulty = IdeaDifficulty.valueOf(getString("difficulty")),
        worksInVersionRange = deserializeVersionRange(getString("minecraft_version_range")),
        testData = deserializeTestData(getString("test_data")),
        categoryData = deserializeCategoryData(getString("category_data")),
        createdBy = getInt("created_by"),
        createdAt = getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault())
    )
}

/**
 * Deserialize author JSON to Author object
 */
private fun deserializeAuthor(json: String): Author {
    val jsonObject = Json.parseToJsonElement(json).jsonObject
    val type = jsonObject["type"]?.jsonPrimitive?.content

    return when (type) {
        "single" -> {
            val name = jsonObject["name"]?.jsonPrimitive?.content ?: ""
            Author.SingleAuthor(name)
        }
        "team" -> {
            val members = jsonObject["members"]?.jsonArray?.map { memberElement ->
                val memberObj = memberElement.jsonObject
                Author.TeamAuthor(
                    name = memberObj["name"]?.jsonPrimitive?.content ?: "",
                    order = memberObj["index"]?.jsonPrimitive?.int ?: 0,
                    role = memberObj["title"]?.jsonPrimitive?.content ?: "",
                    contributions = memberObj["responsibleFor"]?.jsonArray?.map {
                        it.jsonPrimitive.content
                    } ?: emptyList()
                )
            } ?: emptyList()
            Author.Team(members)
        }
        else -> Author.SingleAuthor("Unknown")
    }
}

/**
 * Deserialize sub-authors array from PostgreSQL JSONB array
 */
private fun deserializeSubAuthors(array: Array?): List<Author> {
    if (array == null) return emptyList()

    val jsonArray = array.array as kotlin.Array<*>
    return jsonArray.mapNotNull { jsonStr: Any? ->
        if (jsonStr != null) {
            deserializeAuthor(jsonStr.toString())
        } else {
            null
        }
    }
}

/**
 * Deserialize version range JSON to MinecraftVersionRange object
 */
private fun deserializeVersionRange(json: String): MinecraftVersionRange {
    val jsonObject = Json.parseToJsonElement(json).jsonObject
    val type = jsonObject["type"]?.jsonPrimitive?.content

    return when (type) {
        "bounded" -> {
            val from = jsonObject["from"]?.jsonPrimitive?.content?.let { MinecraftVersion.fromString(it) }
                ?: MinecraftVersion.release(20, 0)
            val to = jsonObject["to"]?.jsonPrimitive?.content?.let { MinecraftVersion.fromString(it) }
                ?: MinecraftVersion.release(20, 0)
            MinecraftVersionRange.Bounded(from, to)
        }
        "lowerBounded" -> {
            val from = jsonObject["from"]?.jsonPrimitive?.content?.let { MinecraftVersion.fromString(it) }
                ?: MinecraftVersion.release(20, 0)
            MinecraftVersionRange.LowerBounded(from)
        }
        "upperBounded" -> {
            val to = jsonObject["to"]?.jsonPrimitive?.content?.let { MinecraftVersion.fromString(it) }
                ?: MinecraftVersion.release(20, 0)
            MinecraftVersionRange.UpperBounded(to)
        }
        "unbounded" -> MinecraftVersionRange.Unbounded
        else -> MinecraftVersionRange.Unbounded
    }
}

/**
 * Deserialize test data JSON array to list of PerformanceTestData
 */
private fun deserializeTestData(json: String): List<PerformanceTestData> {
    val jsonArray = Json.parseToJsonElement(json).jsonArray
    return jsonArray.mapNotNull { element ->
        val obj = element.jsonObject
        val mspt = obj["mspt"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val hardware = obj["hardware"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val versionStr = obj["version"]?.jsonPrimitive?.content ?: return@mapNotNull null

        try {
            PerformanceTestData(
                mspt = mspt,
                hardware = hardware,
                version = MinecraftVersion.fromString(versionStr)
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Deserialize category data JSON to Map
 */
private fun deserializeCategoryData(json: String): Map<String, Any> {
    val jsonObject = Json.parseToJsonElement(json).jsonObject
    return jsonObject.entries.associate { (key, value) ->
        key to parseJsonValue(value)
    }
}

/**
 * Parse JSON value to appropriate Kotlin type
 */
private fun parseJsonValue(element: JsonElement): Any {
    return when (element) {
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.intOrNull != null -> element.int
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
        }
        is JsonArray -> element.map { parseJsonValue(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to parseJsonValue(v) }
    }
}
