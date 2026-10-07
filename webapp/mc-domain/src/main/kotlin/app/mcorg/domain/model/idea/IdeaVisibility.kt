package app.mcorg.domain.model.idea

import app.mcorg.domain.model.user.TokenProfile

/**
 * How widely an idea is visible (MCO-291).
 *
 * A scope ladder, deliberately narrow:
 *
 * - **No DRAFT.** Drafts live in their own table with their own shape — partial, unvalidated,
 *   stage-tracked. An [Idea] is complete by construction. That boundary is about shape, not visibility.
 * - **No WORLD.** "Visible to a world" is a *target*, not a scope: it has to name which world, and an
 *   idea shared with two worlds cannot be one enum value. Sharing belongs in a relation
 *   (`idea_shares`), and a WORLD value here would only ever be a stale cache of "has a share row".
 *
 * The natural next rung is UNLISTED (reachable by link, absent from the hub) — it needs no target,
 * so unlike world-sharing it does fit this ladder. Left unadded until something needs it.
 */
enum class IdeaVisibility {
    /** A personal design. Visible only to its creator. The default for everything newly published. */
    PRIVATE,

    /** On the community hub, visible to everyone. Reaching this state is the privileged step. */
    PUBLIC,
}

/**
 * Whether [viewer] may open an idea with this visibility created by [createdBy]. The one rule behind
 * IdeaVisibilityPlugin and every link to an idea: a link drawn for a viewer this refuses is a 404.
 */
fun IdeaVisibility.isVisibleTo(viewer: TokenProfile, createdBy: Int): Boolean =
    this == IdeaVisibility.PUBLIC || createdBy == viewer.id || viewer.isSuperAdmin
