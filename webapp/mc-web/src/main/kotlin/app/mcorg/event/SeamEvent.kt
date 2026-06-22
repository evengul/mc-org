package app.mcorg.event

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Profile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.User
import app.mcorg.domain.model.user.WorldMember
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * In-process event taxonomy — the foundation layer for the webapp → Discord integration (MCO-228).
 *
 * Every event is world-scoped, timestamped, and attributed to an actor (or the system). Events are
 * fire-and-forget signals emitted at the handler level after a successful pipeline result; they
 * carry no persistence, ordering, or delivery guarantees (those live downstream — see MCO-229).
 *
 * Two families:
 *  - **Domain events** — emitted by handlers after a successful mutation.
 *  - **[DerivedEvent]s** — re-published by [DerivedEventConsumer] from a single domain event. The
 *    consumer ignores derived events as inputs, so derivation is strictly one level deep.
 *
 * The wire contract for downstream consumers is the [EventEnvelope]; [eventType] and [data] define
 * each event's stable serialized shape. Event versioning is deferred until the first schema change.
 *
 * Display names ([actorName], `project_name`, `unblocked_by_name`) are optional enrichment (MCO-239)
 * so downstream renderers can show "Iron Farm — even" instead of "Project #42"; consumers fall back
 * to the ids when a name is absent, so populating them is purely additive.
 */
sealed interface SeamEvent {
    /** World the event belongs to. */
    val worldId: Int

    /** User who triggered the event, or `null` when system-originated. */
    val actorId: Int?

    /** Display name of the acting user, or `null` when system-originated or not populated (MCO-239). */
    val actorName: String?

    /** When the event occurred. */
    val timestamp: Instant

    /** Stable snake_case wire name, e.g. `project_created`. */
    val eventType: String

    /** Event-specific payload for the envelope's `data` field. */
    fun data(): JsonObject
}

/**
 * The display name to attribute an event to, derived from the acting [User] (MCO-239). Used at every
 * publish site so the `when` lives in one place. Member/token profiles expose a display name; a bare
 * [Profile] falls back to its email.
 */
fun User.actorDisplayName(): String = when (this) {
    is WorldMember -> displayName
    is TokenProfile -> displayName
    is Profile -> email
}

/**
 * Marker for events produced by [DerivedEventConsumer] rather than published directly by a handler.
 * The consumer skips these as inputs, enforcing "one level of derivation only".
 */
sealed interface DerivedEvent : SeamEvent

// ── Domain events ────────────────────────────────────────────────────────────

/**
 * A project's resource progress changed. Carries both the item-level delta and the project-level
 * rollup so derived consumers can compute milestones and completion without touching the database.
 */
data class ResourceCountUpdated(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val itemId: String,
    val previousDone: Int,
    val newDone: Int,
    val projectPreviousDone: Int,
    val projectNewDone: Int,
    val projectRequired: Int,
    override val actorName: String? = null,
    val projectName: String? = null,
) : SeamEvent {
    override val eventType = "resource_count_updated"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
        put("item_id", itemId)
        put("previous_done", previousDone)
        put("new_done", newDone)
        put("project_previous_done", projectPreviousDone)
        put("project_new_done", projectNewDone)
        put("project_required", projectRequired)
    }
}

/** An action/item task was toggled complete or incomplete. */
data class TaskToggled(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val taskId: Int,
    val completed: Boolean,
    override val actorName: String? = null,
) : SeamEvent {
    override val eventType = "task_toggled"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("task_id", taskId)
        put("completed", completed)
    }
}

/** A new project was created. */
data class ProjectCreated(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val name: String,
    val type: ProjectType,
    override val actorName: String? = null,
) : SeamEvent {
    override val eventType = "project_created"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("name", name)
        put("type", type.name)
    }
}

/** A project moved between lifecycle states (PENDING → ACTIVE → DONE …). */
data class ProjectStatusChanged(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val previousState: ProjectState,
    val newState: ProjectState,
    override val actorName: String? = null,
    val projectName: String? = null,
) : SeamEvent {
    override val eventType = "project_status_changed"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
        put("previous_state", previousState.name)
        put("new_state", newState.name)
    }
}

/** A production / resource-gathering path was generated for a project's target item. */
data class ProductionPathGenerated(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val itemId: String,
    override val actorName: String? = null,
) : SeamEvent {
    override val eventType = "production_path_generated"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("item_id", itemId)
    }
}

/** A dependency edge `projectId depends on dependsOnProjectId` was added. */
data class DependencyEdgeAdded(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val dependsOnProjectId: Int,
    override val actorName: String? = null,
) : SeamEvent {
    override val eventType = "dependency_edge_added"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("depends_on_project_id", dependsOnProjectId)
    }
}

/**
 * A dependency edge was removed. [unblockedDependentProjectIds] lists projects that became unblocked
 * as a result (computed by the publishing handler, which has database access); the derived consumer
 * fans these out into [ProjectUnblocked] events without re-querying. [projectName] is the name of the
 * completing dependency (this event's [projectId]); the consumer copies it through as the unblocking
 * project's name (MCO-239).
 */
data class DependencyEdgeRemoved(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val dependsOnProjectId: Int,
    val unblockedDependentProjectIds: List<Int> = emptyList(),
    override val actorName: String? = null,
    val projectName: String? = null,
) : SeamEvent {
    override val eventType = "dependency_edge_removed"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
        put("depends_on_project_id", dependsOnProjectId)
    }
}

/** An idea was imported into a world (e.g. from the Discord triage feed). */
data class IdeaImported(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val ideaId: Int,
    val name: String,
    override val actorName: String? = null,
) : SeamEvent {
    override val eventType = "idea_imported"
    override fun data() = buildJsonObject {
        put("idea_id", ideaId)
        put("name", name)
    }
}

// ── Derived events ───────────────────────────────────────────────────────────

/** A project crossed a resource-progress milestone (one of 25 / 50 / 75 / 100 percent). */
data class ResourceMilestoneReached(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val milestone: Int,
    override val actorName: String? = null,
    val projectName: String? = null,
) : DerivedEvent {
    override val eventType = "resource_milestone_reached"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
        put("milestone", milestone)
    }
}

/** A project's resource requirements are fully satisfied. */
data class ProjectResourcesComplete(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    override val actorName: String? = null,
    val projectName: String? = null,
) : DerivedEvent {
    override val eventType = "project_resources_complete"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
    }
}

/** A project became unblocked because a dependency edge was removed. */
data class ProjectUnblocked(
    override val worldId: Int,
    override val actorId: Int?,
    override val timestamp: Instant,
    val projectId: Int,
    val unblockedByProjectId: Int,
    override val actorName: String? = null,
    val projectName: String? = null,
    val unblockedByName: String? = null,
) : DerivedEvent {
    override val eventType = "project_unblocked"
    override fun data() = buildJsonObject {
        put("project_id", projectId)
        put("project_name", projectName)
        put("unblocked_by_project_id", unblockedByProjectId)
        put("unblocked_by_name", unblockedByName)
    }
}
