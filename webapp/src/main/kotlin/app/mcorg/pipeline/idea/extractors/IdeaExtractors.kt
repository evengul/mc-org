package app.mcorg.pipeline.idea.extractors

import app.mcorg.domain.model.idea.*
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.serialization.json.*
import java.sql.Array
import java.sql.ResultSet
import java.time.ZoneId

fun ResultSet.toIdea(): Idea {
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
fun deserializeVersionRange(json: String): MinecraftVersionRange {
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