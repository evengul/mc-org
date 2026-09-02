package app.mcorg.domain.model.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.time.ZonedDateTime

data class World(
    val id: Int,
    val name: String,
    val description: String,
    val version: MinecraftVersion,
    val completedProjects: Int,
    val totalProjects: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
    val pinned: Boolean = false,
    val lastOpenedAt: ZonedDateTime? = null,
    /**
     * Raw-gather demand at or above this is farm-scale (MCO-401). Defaults to
     * [DEFAULT_FARM_SCALE_THRESHOLD]; editable per world in settings.
     */
    val farmScaleThreshold: Int = DEFAULT_FARM_SCALE_THRESHOLD,
    /**
     * Which tree this world farms, e.g. `"birch"` (MCO-409) — the one answer that settles
     * `#planks`, `#wooden_slabs` and `#logs` instead of asking three times.
     *
     * **A default for recipe ingredients, never for the goal list.** Every tag it settles is
     * something a recipe consumes; a build that asked for oak planks still gets oak planks,
     * because a target is always a concrete item. Null means unanswered — those tags stay open
     * and asked, since which wood you farm is a real preference rather than something to assume.
     *
     * Values come from `MemberPrior.SPECIES`; a project wanting a different wood overrides per
     * tag, which still wins over this.
     */
    val preferredWoodSpecies: String? = null,
    /**
     * Projects split by lifecycle state (MCO-468). [completedProjects] / [totalProjects]
     * stay for the public API's sake; this is what the Worlds page renders.
     */
    val projectTally: WorldProjectTally = WorldProjectTally.EMPTY,
) {
    companion object {
        /** One shulker box — the unit players already judge bulk in. */
        const val DEFAULT_FARM_SCALE_THRESHOLD = 1728
    }
}
