package app.mcorg.cli

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.domain.Production
import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.idea.CreateIdeaInput
import app.mcorg.pipeline.idea.CreateIdeaStep
import app.mcorg.pipeline.idea.commonsteps.IdeaProductionModeInput
import app.mcorg.pipeline.idea.single.PublishIdeaToHubStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.project.CreateProjectInput
import app.mcorg.pipeline.project.CreateProjectStep
import app.mcorg.pipeline.project.UpdateProjectStateStep
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.project.resources.ProjectProductionInput
import app.mcorg.pipeline.project.resources.UpsertProjectProductionStep
import app.mcorg.pipeline.resources.CreateResourceGatheringItemInput
import app.mcorg.pipeline.resources.CreateResourceGatheringItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.ordering.insertManualOrdering
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Gives the demo sign-in user a small, deterministic world to look at (MCO-657).
 *
 * A cloud session starts on an empty database, so every visual check of the planner, roadmap or
 * "Worth a farm" used to begin with building a world by hand. This writes one, through the app's
 * own insert steps rather than SQL of its own, so a migration that changes a column changes the
 * seed with it instead of breaking it silently.
 *
 * ```
 * mvn -pl mc-web exec:java@seed-demo                         # latest ingested version
 * mvn -pl mc-web exec:java@seed-demo -Dexec.args="version=26.3.0"
 * webapp/scripts/cloud-setup.sh --seed                       # the usual way in: ingest, then this
 * ```
 *
 * **Idempotent.** The world is keyed on [DEMO_WORLD_NAME] among the demo user's worlds, each idea
 * on its name and author; whatever exists is left alone. A run that fails part-way removes what it
 * wrote, so a half-built world never satisfies the key.
 *
 * **Refuses to run with `ENV=PRODUCTION`.** It writes as whoever `DEMO_USER` names.
 *
 * Writes straight to the database, so a server that is already running keeps its caches: start
 * (or restart) `run.sh` after seeding.
 */
fun main(args: Array<String>) {
    AppConfig.initOrExit()
    val exitCode = runBlocking {
        try {
            if (AppConfig.env == Production) {
                System.err.println("seed-demo: refusing to run with ENV=PRODUCTION")
                return@runBlocking 1
            }
            val username = AppConfig.demoUser ?: run {
                System.err.println("seed-demo: DEMO_USER is unset, so there is no demo user to seed for")
                return@runBlocking 1
            }
            val version = args.firstNotNullOfOrNull { it.removePrefix("version=").takeIf { v -> v != it } }
            val outcome = seedDemo(username, version)
            println("seed-demo: ${outcome.summary()}")
            0
        } catch (e: SeedFailure) {
            System.err.println("seed-demo: ${e.message}")
            1
        } finally {
            Database.shutdown()
        }
    }
    exitProcess(exitCode)
}

const val DEMO_WORLD_NAME = "Demo World"

data class SeedOutcome(
    val worldId: Int,
    val worldCreated: Boolean,
    val version: String,
    val ideasCreated: Int,
) {
    fun summary(): String =
        (if (worldCreated) "created" else "already seeded:") +
            " '$DEMO_WORLD_NAME' (id $worldId, $version), $ideasCreated new idea(s)"
}

class SeedFailure(message: String) : RuntimeException(message)

/**
 * Seeds the demo user's world and ideas. [version] null means the newest ingested version.
 */
suspend fun seedDemo(username: String, version: String? = null): SeedOutcome {
    // The same profile DemoSignInPipeline builds, so the first sign-in finds this user.
    val user = CreateUserIfNotExistsStep.process(MinecraftProfile("$username-uuid", username, isDemoUser = true))
        .orFail("could not create the demo user")

    val worldVersion = resolveVersion(version)
    val items = itemNames(worldVersion, SEED_ITEM_IDS)
    val missing = SEED_ITEM_IDS - items.keys
    if (missing.isNotEmpty()) {
        throw SeedFailure("$worldVersion lacks ${missing.sorted().joinToString()}; ingest a version that has them")
    }

    val createdIdeas = mutableListOf<Int>()
    var createdWorld: Int? = null
    try {
        for (idea in SEED_IDEAS) {
            if (ideaExists(idea.name, user.id)) continue
            val id = CreateIdeaStep(user.id).process(idea).orFail("could not create idea '${idea.name}'")
            createdIdeas += id
            PublishIdeaToHubStep.process(id).orFail("could not publish idea '${idea.name}'")
        }

        existingWorld(user.id)?.let { return SeedOutcome(it, false, worldVersion.toString(), createdIdeas.size) }

        val worldId = CreateWorldStep(user)
            .process(CreateWorldInput(DEMO_WORLD_NAME, "Seeded by cloud-setup.sh --seed (MCO-657).", worldVersion))
            .orFail("could not create the world")
        createdWorld = worldId

        val projectIds = SEED_PROJECTS.associate { project -> project.name to seedProject(worldId, project, items) }
        for ((first, then, reason) in SEED_ORDERINGS) {
            insertManualOrdering(projectIds.getValue(first), projectIds.getValue(then), reason)
                .orFail("could not order '$first' before '$then'")
        }
        return SeedOutcome(worldId, true, worldVersion.toString(), createdIdeas.size)
    } catch (e: Throwable) {
        // Leave nothing half-built behind: the world is the idempotency key, and a world missing
        // its projects would be taken for a finished seed on the next run.
        createdWorld?.let { deleteWorld.process(it) }
        createdIdeas.forEach { deleteIdea.process(it) }
        throw e
    }
}

private suspend fun seedProject(worldId: Int, project: SeedProject, items: Map<String, String>): Int {
    val id = CreateProjectStep(worldId)
        .process(CreateProjectInput(project.name, project.description, project.type))
        .orFail("could not create project '${project.name}'")
    if (project.stage != ProjectStage.IDEA) {
        UpdateProjectStageStep(id).process(project.stage).orFail("could not move '${project.name}' to ${project.stage}")
    }
    if (project.state != ProjectState.PENDING) {
        UpdateProjectStateStep(id).process(project.state).orFail("could not set '${project.name}' to ${project.state}")
    }
    for ((itemId, amount) in project.resources) {
        CreateResourceGatheringItemStep(id)
            .process(CreateResourceGatheringItemInput(items.getValue(itemId), itemId, amount))
            .orFail("could not add $itemId to '${project.name}'")
    }
    for ((itemId, rate) in project.produces) {
        UpsertProjectProductionStep(id, modeId = null)
            .process(ProjectProductionInput(itemId, items.getValue(itemId), rate))
            .orFail("could not record '${project.name}' producing $itemId")
    }
    return id
}

private suspend fun resolveVersion(requested: String?): MinecraftVersion.Release {
    val ingested = GetSupportedVersionsStep.process(Unit).orFail("could not read the ingested versions")
    if (ingested.isEmpty()) throw SeedFailure("no Minecraft data ingested; run cloud-setup.sh --ingest first")
    if (requested == null) return ingested.maxWith { a, b -> a.compareTo(b) }
    return ingested.firstOrNull { it.toString() == requested }
        ?: throw SeedFailure("version $requested is not ingested (have: ${ingested.joinToString()})")
}

private suspend fun itemNames(version: MinecraftVersion.Release, ids: Set<String>): Map<String, String> =
    DatabaseSteps.query<Unit, Map<String, String>>(
        sql = SafeSQL.select("SELECT item_id, item_name FROM minecraft_items WHERE version = ? AND item_id = ANY(?)"),
        parameterSetter = { statement, _ ->
            statement.setString(1, version.toString())
            statement.setArray(2, statement.connection.createArrayOf("text", ids.toTypedArray()))
        },
        resultMapper = { rs -> buildMap { while (rs.next()) put(rs.getString("item_id"), rs.getString("item_name")) } },
    ).process(Unit).orFail("could not read item names")

private suspend fun existingWorld(userId: Int): Int? =
    DatabaseSteps.query<Unit, Int?>(
        sql = SafeSQL.select("""
            SELECT w.id FROM world w
            JOIN world_members m ON m.world_id = w.id
            WHERE w.name = ? AND m.user_id = ?
            ORDER BY w.id LIMIT 1
        """.trimIndent()),
        parameterSetter = { statement, _ ->
            statement.setString(1, DEMO_WORLD_NAME)
            statement.setInt(2, userId)
        },
        resultMapper = { rs -> if (rs.next()) rs.getInt("id") else null },
    ).process(Unit).orFail("could not look for an existing demo world")

private suspend fun ideaExists(name: String, userId: Int): Boolean =
    DatabaseSteps.query<Unit, Boolean>(
        sql = SafeSQL.select("SELECT 1 FROM ideas WHERE name = ? AND created_by = ?"),
        parameterSetter = { statement, _ ->
            statement.setString(1, name)
            statement.setInt(2, userId)
        },
        resultMapper = { rs -> rs.next() },
    ).process(Unit).orFail("could not look for idea '$name'")

private val deleteWorld = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM world WHERE id = ?"),
    parameterSetter = { statement, id -> statement.setInt(1, id) },
)

private val deleteIdea = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM ideas WHERE id = ?"),
    parameterSetter = { statement, id -> statement.setInt(1, id) },
)

private fun <E, S> Result<E, S>.orFail(what: String): S = when (this) {
    is Result.Success -> value
    // The failure's class only: a database error's detail can carry row values (logging.md).
    is Result.Failure -> throw SeedFailure("$what (${error?.let { it::class.simpleName }})")
}

// ---------------------------------------------------------------------------------------------
// The dataset. Small on purpose: enough for each page to have something real to show.
//
// * Iron Farm is built and supplies iron, so the planner shows a SUPPLIED line.
// * Storage Hall is gathering, with crafted lines (hoppers, chests) the planner has to resolve.
// * Main Base needs stone bricks, planks and glass well past the world's 1,728 farm-scale line,
//   so "Worth a farm" has stone and oak logs to offer designs for, and the ideas below produce
//   them. Glass also asks "Sand or Red Sand?", so the plan has an open question; once it is
//   answered with Sand, the Sand Duper matches as well.
// * Main Base comes after Storage Hall, so the roadmap has an edge to lay out.
// ---------------------------------------------------------------------------------------------

data class SeedProject(
    val name: String,
    val description: String,
    val type: ProjectType,
    val stage: ProjectStage,
    val state: ProjectState,
    val resources: Map<String, Int> = emptyMap(),
    val produces: Map<String, Int> = emptyMap(),
)

val SEED_PROJECTS = listOf(
    SeedProject(
        "Iron Farm", "A villager-based iron farm by spawn.", ProjectType.FARMING,
        ProjectStage.COMPLETED, ProjectState.DONE,
        produces = mapOf("minecraft:iron_ingot" to 1200),
    ),
    SeedProject(
        "Storage Hall", "Sorting system and bulk chests.", ProjectType.REDSTONE,
        ProjectStage.RESOURCE_GATHERING, ProjectState.ACTIVE,
        resources = mapOf(
            "minecraft:chest" to 216,
            "minecraft:hopper" to 96,
            "minecraft:comparator" to 48,
            "minecraft:redstone" to 128,
            "minecraft:stone_bricks" to 640,
        ),
    ),
    SeedProject(
        "Sugar Cane Farm", "Observer-driven cane farm for rockets.", ProjectType.FARMING,
        ProjectStage.PLANNING, ProjectState.PENDING,
        resources = mapOf(
            "minecraft:observer" to 24,
            "minecraft:piston" to 24,
            "minecraft:hopper" to 12,
            "minecraft:sugar_cane" to 24,
        ),
    ),
    SeedProject(
        "Main Base", "The big build: a stone-brick keep with an oak hall.", ProjectType.BUILDING,
        ProjectStage.DESIGN, ProjectState.PENDING,
        resources = mapOf(
            "minecraft:stone_bricks" to 3000,
            "minecraft:oak_planks" to 8000,
            "minecraft:glass" to 2000,
            "minecraft:lantern" to 64,
        ),
    ),
    SeedProject(
        "Nether Hub", "Ice-road hub, not designed yet.", ProjectType.EXPLORATION,
        ProjectStage.IDEA, ProjectState.PENDING,
    ),
)

/** (first, then, reason): `then` depends on `first`. */
val SEED_ORDERINGS = listOf(
    Triple("Storage Hall", "Main Base", "The base's materials need somewhere to go."),
)

private fun seedIdea(name: String, description: String, produces: Map<String, Int>, requirements: Map<String, Int>) =
    CreateIdeaInput(
        name = name,
        description = description,
        category = IdeaCategory.FARM,
        difficulty = IdeaDifficulty.MID_GAME,
        labels = emptyList(),
        author = Author.SingleAuthor("Seam demo"),
        subAuthors = emptyList(),
        versionRange = MinecraftVersionRange.Unbounded,
        testData = null,
        itemRequirements = requirements,
        productionModes = listOf(IdeaProductionModeInput(name = "", rates = produces)),
        categoryData = emptyMap(),
    )

val SEED_IDEAS = listOf(
    seedIdea(
        "Stone Generator", "Piston-fed stone generator with a hopper line.",
        produces = mapOf("minecraft:stone" to 12000),
        requirements = mapOf("minecraft:piston" to 8, "minecraft:observer" to 4, "minecraft:hopper" to 4),
    ),
    seedIdea(
        "Oak Tree Farm", "Bonemeal-driven oak farm.",
        produces = mapOf("minecraft:oak_log" to 3000),
        requirements = mapOf("minecraft:piston" to 12, "minecraft:observer" to 6, "minecraft:hopper" to 6),
    ),
    seedIdea(
        "Sand Duper", "Portal-based sand duper.",
        produces = mapOf("minecraft:sand" to 8000),
        requirements = mapOf("minecraft:piston" to 4, "minecraft:hopper" to 6, "minecraft:rail" to 8),
    ),
)

val SEED_ITEM_IDS: Set<String> = buildSet {
    SEED_PROJECTS.forEach { addAll(it.resources.keys); addAll(it.produces.keys) }
    SEED_IDEAS.forEach { idea -> addAll(idea.itemRequirements.keys); idea.productionModes.forEach { addAll(it.rates.keys) } }
}
