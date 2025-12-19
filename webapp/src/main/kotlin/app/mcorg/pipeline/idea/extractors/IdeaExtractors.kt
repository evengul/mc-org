package app.mcorg.pipeline.idea.extractors

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.RatingSummary
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.ZoneId

fun ResultSet.toIdea(): Idea {
    return Idea(
        id = getInt("id"),
        name = getString("name"),
        description = getString("description"),
        category = IdeaCategory.valueOf(getString("category")),
        author = Json.decodeFromString(Author.serializer(), getString("author")),
        subAuthors = emptyList(),
        labels = emptyList(),
        favouritesCount = getInt("favourites_count"),
        rating = RatingSummary(
            average = getDouble("rating_average"),
            total = getInt("rating_count")
        ),
        difficulty = IdeaDifficulty.valueOf(getString("difficulty")),
        worksInVersionRange = Json.decodeFromString(MinecraftVersionRange.serializer(), getString("minecraft_version_range")),
        testData = emptyList(),
        categoryData = Json.decodeFromString(MapSerializer(String.serializer(), CategoryValue.serializer()), getString("category_data")),
        createdBy = getInt("created_by"),
        createdAt = getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault())
    )
}
