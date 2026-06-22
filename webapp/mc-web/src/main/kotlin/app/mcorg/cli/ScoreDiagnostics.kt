package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.engine.plan.ScoreDiagnostics
import app.mcorg.engine.plan.ScoreDiagnostics.ItemReport
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Standalone read-only diagnostic: prints the selection-scorer factor breakdown
 * for one or more items against the real ingested graph, in the order
 * [app.mcorg.engine.plan.PlanSelector] would rank candidates. Built to chase the
 * "why did it pick chest loot / the wandering trader over mining?" questions from
 * the gathering-planner workflow review without changing any scoring logic.
 *
 * Invoke (sources local.env for the worktree's DB):
 *
 * ```
 * mvn -pl mc-web exec:java@score-diagnostics \
 *   -Dexec.args="world=11 demand=64 cobblestone sand white_dye emerald iron_ingot gunpowder honey_bottle"
 * ```
 *
 * Args (all optional, any order):
 * - `version=<mc version>` — pin the version; otherwise auto-picks the single
 *   ingested version, or `world=<id>` resolves it from that world.
 * - `world=<id>` — resolve the version from a world row.
 * - `demand=<n>` — the requested amount fed to the scorer (default 64). Raise it
 *   above the recipe threshold (100) to see the bulk-crafting bonus kick in.
 * - everything else — item ids; a bare `sand` becomes `minecraft:sand`. With no
 *   item ids, a default set covering the flagged notes is used.
 */
private val DEFAULT_ITEMS = listOf(
    "cobblestone", "sand", "gravel", "white_dye", "emerald", "iron_ingot",
    "gunpowder", "honey_bottle", "tnt", "detector_rail", "white_concrete"
)

fun main(args: Array<String>) {
    val exitCode = runBlocking {
        try {
            run(args.toList())
        } catch (e: Throwable) {
            System.err.println("score-diagnostics failed: ${e.message}")
            e.printStackTrace()
            2
        } finally {
            Database.shutdown()
        }
    }
    exitProcess(exitCode)
}

private suspend fun run(args: List<String>): Int {
    var version: String? = null
    var worldId: Int? = null
    var demand = 64L
    val items = mutableListOf<String>()

    for (arg in args) {
        when {
            arg.startsWith("version=") -> version = arg.substringAfter('=')
            arg.startsWith("world=") -> worldId = arg.substringAfter('=').toIntOrNull()
            arg.startsWith("demand=") -> demand = arg.substringAfter('=').toLongOrNull() ?: demand
            else -> items.add(normalizeId(arg))
        }
    }

    val resolvedVersion = version ?: resolveVersion(worldId) ?: return 1
    val itemIds = items.ifEmpty { DEFAULT_ITEMS.map { normalizeId(it) } }

    val graph = when (val r = GetItemSourceGraphForVersionStep.process(resolvedVersion)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            System.err.println("No graph for version '$resolvedVersion' (${r.error}). Is it ingested?")
            return 1
        }
    }

    println("Score diagnostics · version $resolvedVersion · demand $demand · recipe threshold 100")
    println("Sources: ${graph.getSourceCount()}, items: ${graph.getItemCount()}")
    for (id in itemIds) {
        printReport(ScoreDiagnostics.report(graph, id, demand))
    }
    return 0
}

private fun normalizeId(raw: String): String = if (raw.contains(':')) raw else "minecraft:$raw"

private suspend fun resolveVersion(worldId: Int?): String? {
    if (worldId != null) {
        val v = worldVersionQuery.process(worldId)
        val resolved = (v as? Result.Success)?.value
        if (resolved == null) System.err.println("No version for world $worldId")
        return resolved
    }
    val versions = (distinctVersionsQuery.process(Unit) as? Result.Success)?.value.orEmpty()
    return when (versions.size) {
        0 -> { System.err.println("No ingested versions found."); null }
        1 -> versions.single()
        else -> { System.err.println("Multiple versions ingested: $versions. Pass version=<x> or world=<id>."); null }
    }
}

private val worldVersionQuery = DatabaseSteps.query<Int, String?>(
    sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
    parameterSetter = { ps, id -> ps.setInt(1, id) },
    resultMapper = { rs -> if (rs.next()) rs.getString("version") else null }
)

private val distinctVersionsQuery = DatabaseSteps.query<Unit, List<String>>(
    sql = SafeSQL.select("SELECT DISTINCT version FROM resource_source ORDER BY version"),
    parameterSetter = { _, _ -> },
    resultMapper = { rs -> buildList { while (rs.next()) add(rs.getString("version")) } }
)

private fun printReport(report: ItemReport) {
    println()
    if (!report.found) {
        println("=== ${report.itemId} — NOT IN GRAPH (no item node; truly unknown id) ===")
        return
    }
    if (report.candidates.isEmpty()) {
        println("=== ${report.itemName} (${report.itemId}) — BLOCKED: 0 candidate sources ===")
        return
    }
    println(
        "=== ${report.itemName} (${report.itemId}) · ${report.candidates.size} candidates · " +
            "hasRecipeSibling=${report.hasRecipeSibling} ==="
    )
    for (c in report.candidates) {
        val marker = if (c.selected) "▶" else " "
        val factors = buildList {
            add("base ${c.base}")
            if (c.efficiency != 0) add("eff +${c.efficiency}")
            if (c.supplied != 0) add("sup +${c.supplied}")
            if (c.recipeThreshold != 0) add("thr +${c.recipeThreshold}")
            if (c.selfBlockLoot != 0) add("selfBlock -${c.selfBlockLoot}")
            if (c.lowYield != 0) add("lowYield -${c.lowYield}")
            if (c.requirementPenalty != 0) add("req -${c.requirementPenalty} (${c.requirementCount})")
            if (c.depthPenalty != 0) add("depth -${c.depthPenalty} (d${c.chainDepth})")
        }.joinToString("  ")
        println(" $marker %5d  %-22s %s".format(c.total, c.method, c.sourceKey))
        println("          $factors")
        if (c.requiredItemIds.isNotEmpty()) {
            println("          requires: ${c.requiredItemIds.joinToString(", ")}")
        }
    }
}
