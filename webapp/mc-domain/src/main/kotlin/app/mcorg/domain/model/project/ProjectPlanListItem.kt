package app.mcorg.domain.model.project

import app.mcorg.domain.model.minecraft.MinecraftLocation

data class ProjectPlanListItem(
    val id: Int,
    val name: String,
    val stage: ProjectStage,
    val resourceDefinitionCount: Int,
    val blockedByProjects: List<NamedProjectId>,
    val blocksProjects: List<NamedProjectId>,
    val location: MinecraftLocation
) {
    val blockedByCount: Int get() = blockedByProjects.size
    val blocksCount: Int get() = blocksProjects.size
}
