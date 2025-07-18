package app.mcorg.domain.model.v2.resources

import java.time.ZonedDateTime

data class ResourceMap(
    val id: Int,
    val worldId: Int,
    val name: String,
    val description: String,
    val updatedAt: ZonedDateTime,
    val locations: List<ResourceLocation>,
)
