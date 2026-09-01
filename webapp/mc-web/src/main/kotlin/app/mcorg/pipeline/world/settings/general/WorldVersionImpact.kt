package app.mcorg.pipeline.world.settings.general

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * MCO-157: what a world's stored rows lose when it plans against a different Minecraft version.
 *
 * A world's *derived* state already survives a version change on its own — gathering plans are
 * re-derived per request, `project_demand`'s fingerprint has the world version as its first field
 * ([app.mcorg.pipeline.resources.DemandFingerprint]), and the item-source graph is cached per
 * version. What does not survive is the stored side: four tables hold Minecraft item ids as bare
 * strings, and Mojang does remove them. `minecraft:grass` went in 1.20.3, `minecraft:scute` in
 * 1.20.5, `minecraft:chain` in 1.21.9 — all three renames rather than deletions, which is exactly
 * why the ids look fine right up until nothing resolves them.
 *
 * This is the one derivation behind both surfaces (see also `ImportWarnings`, same shape):
 *
 *  - **Before a switch** — [worldVersionImpact] against the *target* version is the preflight the
 *    settings page shows, so "switch to 1.21.11" names the eleven rows it will strand.
 *  - **After a switch** — [projectVersionGaps] against the world's *current* version is what lets
 *    a blocked plan row say "not in Minecraft 1.21.11" instead of the generic "no feasible source
 *    found". Same question, one version later.
 *
 * Nothing here writes. A stranded row is kept and named, never rewritten or deleted: the id that
 * replaces `minecraft:grass` is a curated fact this code does not have (MCO-470), and guessing it
 * would quietly change what a user asked for.
 */
enum class VersionImpactUsage(val label: String) {
    /** A resource the project needs — `resource_gathering`. */
    REQUIREMENT("Needed"),

    /** An item the project's farm produces — `project_productions`. */
    PRODUCTION("Produced"),

    /** Collected-so-far counts — `resource_gathering_progress`. */
    PROGRESS("Progress"),

    /** A pinned source or chosen tag member — `resource_gathering_plan_override`. */
    PINNED_CHOICE("Pinned choice"),
    ;

    companion object {
        fun fromDbName(name: String): VersionImpactUsage? = entries.find { it.name == name }
    }
}

/** One stored id the candidate version has no catalog entry for. */
data class MissingItem(
    val itemId: String,
    /** Best display name the stored row carries; falls back to the id when the row has no name. */
    val name: String,
    val usages: Set<VersionImpactUsage>,
) {
    /** Tag ids ("#minecraft:planks") read differently to a user than item ids do. */
    val isTag: Boolean get() = itemId.startsWith("#")
}

/** The stranded ids of one project, so the preflight can group by the thing a user would go fix. */
data class ProjectVersionImpact(
    val projectId: Int,
    val projectName: String,
    val items: List<MissingItem>,
)

/**
 * Everything in [worldId] that the catalog of [version] does not contain.
 *
 * Empty is the common and the good case — most version steps remove nothing at all, and a world
 * whose ids all survive can switch with no caveat to read.
 */
data class WorldVersionImpact(
    val version: String,
    val projects: List<ProjectVersionImpact>,
) {
    val isEmpty: Boolean get() = projects.isEmpty()
    val projectCount: Int get() = projects.size
    val itemCount: Int get() = projects.sumOf { it.items.size }

    /** The distinct stranded ids, for callers that only need the set. */
    val itemIds: Set<String> get() = projects.flatMapTo(mutableSetOf()) { p -> p.items.map { it.itemId } }

    companion object {
        fun empty(version: String) = WorldVersionImpact(version, emptyList())
    }
}

/**
 * Every id stored against a world, tagged with the project that owns it and how it is used, left
 * joined against the candidate version's items *and* tags — an override's `item_id` is a tag as
 * often as an item, and a tag that still exists is not a gap.
 *
 * `project_productions` has no display name for the id itself (its `name` names the production,
 * not the item), so it contributes NULL and loses the `MIN` to any row that does carry a name.
 */
private val impactQuery = DatabaseSteps.query<VersionImpactInput, List<ProjectVersionImpact>>(
    sql = SafeSQL.with(
        """
        WITH world_projects AS (
            SELECT id, name FROM projects WHERE world_id = ?
        ), stored AS (
            SELECT rg.project_id, rg.item_id AS id, rg.name AS label, 'REQUIREMENT' AS usage
            FROM resource_gathering rg
            JOIN world_projects wp ON wp.id = rg.project_id
            UNION ALL
            SELECT pp.project_id, pp.item_id, NULL, 'PRODUCTION'
            FROM project_productions pp
            JOIN world_projects wp ON wp.id = pp.project_id
            UNION ALL
            SELECT rgp.project_id, rgp.item_id, NULL, 'PROGRESS'
            FROM resource_gathering_progress rgp
            JOIN world_projects wp ON wp.id = rgp.project_id
            UNION ALL
            SELECT rgo.project_id, rgo.item_id, NULL, 'PINNED_CHOICE'
            FROM resource_gathering_plan_override rgo
            JOIN world_projects wp ON wp.id = rgo.project_id
            UNION ALL
            SELECT rgo.project_id, rgo.tag_member, NULL, 'PINNED_CHOICE'
            FROM resource_gathering_plan_override rgo
            JOIN world_projects wp ON wp.id = rgo.project_id
            WHERE rgo.tag_member IS NOT NULL
        )
        SELECT
            s.project_id,
            wp.name AS project_name,
            s.id AS item_id,
            MIN(s.label) AS item_name,
            STRING_AGG(DISTINCT s.usage, ',') AS usages
        FROM stored s
        JOIN world_projects wp ON wp.id = s.project_id
        LEFT JOIN minecraft_items mi ON mi.version = ? AND mi.item_id = s.id
        LEFT JOIN minecraft_tag mt ON mt.version = ? AND mt.tag = s.id
        WHERE mi.item_id IS NULL AND mt.tag IS NULL
        GROUP BY s.project_id, wp.name, s.id
        ORDER BY wp.name, s.id
        """.trimIndent()
    ),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.worldId)
        statement.setString(2, input.version)
        statement.setString(3, input.version)
    },
    resultMapper = { resultSet ->
        val byProject = LinkedHashMap<Int, ProjectVersionImpact>()
        while (resultSet.next()) {
            val projectId = resultSet.getInt("project_id")
            val itemId = resultSet.getString("item_id")
            val item = MissingItem(
                itemId = itemId,
                name = resultSet.getString("item_name") ?: itemId,
                usages = resultSet.getString("usages")
                    .orEmpty()
                    .split(',')
                    .mapNotNullTo(mutableSetOf()) { VersionImpactUsage.fromDbName(it) },
            )
            val existing = byProject[projectId]
            byProject[projectId] = existing?.copy(items = existing.items + item)
                ?: ProjectVersionImpact(
                    projectId = projectId,
                    projectName = resultSet.getString("project_name"),
                    items = listOf(item),
                )
        }
        byProject.values.toList()
    },
)

private data class VersionImpactInput(val worldId: Int, val version: String)

/** What [worldId]'s stored rows lose if it plans against [version]. */
suspend fun worldVersionImpact(
    worldId: Int,
    version: String,
): Result<AppFailure.DatabaseError, WorldVersionImpact> =
    impactQuery.process(VersionImpactInput(worldId, version))
        .map { WorldVersionImpact(version, it) }

/**
 * The stranded ids of one project under its world's *current* version, for the plan surface.
 *
 * Scoped to the project rather than reusing [worldVersionImpact] so rendering one project does not
 * pay for its siblings, and it takes the version from the world row instead of an argument — the
 * caller is asking "what is broken right now", and a version passed in could disagree with the one
 * the plan was just built against.
 */
private val projectGapQuery = DatabaseSteps.query<Int, Set<String>>(
    sql = SafeSQL.with(
        """
        WITH target AS (
            SELECT p.id AS project_id, w.version AS version
            FROM projects p
            JOIN world w ON w.id = p.world_id
            WHERE p.id = ?
        ), stored AS (
            SELECT rg.item_id AS id FROM resource_gathering rg JOIN target t ON t.project_id = rg.project_id
            UNION
            SELECT pp.item_id FROM project_productions pp JOIN target t ON t.project_id = pp.project_id
            UNION
            SELECT rgp.item_id FROM resource_gathering_progress rgp JOIN target t ON t.project_id = rgp.project_id
            UNION
            SELECT rgo.item_id FROM resource_gathering_plan_override rgo JOIN target t ON t.project_id = rgo.project_id
            UNION
            SELECT rgo.tag_member FROM resource_gathering_plan_override rgo JOIN target t ON t.project_id = rgo.project_id
            WHERE rgo.tag_member IS NOT NULL
        )
        SELECT s.id AS item_id
        FROM stored s
        CROSS JOIN target t
        LEFT JOIN minecraft_items mi ON mi.version = t.version AND mi.item_id = s.id
        LEFT JOIN minecraft_tag mt ON mt.version = t.version AND mt.tag = s.id
        WHERE mi.item_id IS NULL AND mt.tag IS NULL
        """.trimIndent()
    ),
    parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
    resultMapper = { resultSet ->
        buildSet {
            while (resultSet.next()) add(resultSet.getString("item_id"))
        }
    },
)

/**
 * Best-effort: a plan that renders is worth more than a precise label, so a failed lookup yields
 * no labels rather than no page.
 */
suspend fun projectVersionGaps(projectId: Int): Set<String> =
    projectGapQuery.process(projectId).getOrNull().orEmpty()

/**
 * [projectVersionGaps], asked only when a plan has a blocked row to explain.
 *
 * The gap lookup exists to tell one kind of blocked row from another, so a plan with none has
 * nothing to explain and pays nothing — which is every healthy world. Shared by the three surfaces
 * that render a plan (full page, lens fragment, chain fragment) so they cannot label it differently.
 */
suspend fun versionGapsForPlan(projectId: Int, plan: GatheringPlan?): Set<String> =
    if (plan?.activityList?.any { it.status == PlanNodeStatus.BLOCKED } == true) {
        projectVersionGaps(projectId)
    } else {
        emptySet()
    }
