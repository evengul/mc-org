package app.mcorg.domain.model.project

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.minecraft.MinecraftLocation
import java.time.ZonedDateTime

data class Project(
    val id: Int,
    val worldId: Int,
    val name: String,
    val description: String,
    val type: ProjectType,
    val stage: ProjectStage,
    val state: ProjectState,
    val location: MinecraftLocation?,
    val tasksTotal: Int,
    val tasksCompleted: Int,
    val importedFromIdea: ImportedIdea? = null,
    /**
     * Why and when the project last stopped supplying (MCO-541). Written on entering
     * DECOMMISSIONED and kept afterwards as history, so read them only while [state] is
     * DECOMMISSIONED.
     */
    val decommissionReason: String? = null,
    val decommissionedAt: ZonedDateTime? = null,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)

/**
 * The idea a project was imported from. Carries the idea's [visibility] and [createdBy] because the
 * project is seen by its whole world while the idea may be visible only to whoever imported it —
 * a link back is drawn only for a viewer [IdeaVisibility.isVisibleTo] lets in.
 */
data class ImportedIdea(
    val id: Int,
    val name: String,
    val visibility: IdeaVisibility,
    val createdBy: Int,
)
