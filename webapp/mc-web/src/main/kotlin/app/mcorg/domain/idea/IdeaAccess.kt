package app.mcorg.domain.idea

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.project.ImportedIdea
import app.mcorg.domain.model.user.TokenProfile

/**
 * Reads `ideas.visibility`. A value this build does not know (a rung added by a migration ahead of
 * the code, or left behind by a rollback) reads as [IdeaVisibility.PRIVATE], so it fails closed:
 * the idea stays visible to its creator and to superadmins, and to no one else.
 */
fun ideaVisibilityOf(stored: String): IdeaVisibility =
    IdeaVisibility.entries.firstOrNull { it.name == stored } ?: IdeaVisibility.PRIVATE

/**
 * The visibility half of what the idea route checks: IdeaVisibilityPlugin applies this, after
 * IdeaParamPlugin has refused an inactive idea. Listings filter on the same rule in SQL
 * (GetIdeaProducersStep, IdeaSqlBuilder), without the superadmin bypass.
 */
fun IdeaVisibility.isVisibleTo(viewer: TokenProfile, createdBy: Int): Boolean =
    this == IdeaVisibility.PUBLIC || createdBy == viewer.id || viewer.isSuperAdmin

/** Whether the idea route would open this idea for [viewer]: both checks, so a link drawn on it is never a 404. */
fun ImportedIdea.canBeOpenedBy(viewer: TokenProfile): Boolean =
    isActive && visibility.isVisibleTo(viewer, createdBy)
