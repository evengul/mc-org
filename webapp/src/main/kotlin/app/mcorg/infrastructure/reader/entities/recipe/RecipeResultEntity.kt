package app.mcorg.infrastructure.reader.entities.recipe

import kotlinx.serialization.Serializable

@Serializable
data class RecipeResultEntity(
    val count: Int = 1,
    val id: String,
    val components: Map<String, List<RecipeResultComponentEntity>>? = null
)

@Serializable
data class RecipeResultComponentEntity(
    val id: String,
    val duration: Int? = null,
)
