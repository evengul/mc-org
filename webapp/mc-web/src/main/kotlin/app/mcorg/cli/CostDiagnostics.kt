package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.EffortTable
import app.mcorg.engine.plan.ScoreDiagnostics
import app.mcorg.engine.plan.UnitCostModel
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Read-only comparison of the shipped [app.mcorg.engine.plan.SelectionScorer] against the
 * proposed [UnitCostModel], over every produced item in the real ingested graph.
 * **Changes nothing.** It exists to answer the question that has to come before a rewrite:
 * where do the two models actually differ, and is each difference a fix or a regression?
 *
 * The shipped model has been debugged against real worlds for months, so agreement is the
 * expected case and the thing to check first — a cost model that disagreed everywhere would
 * be throwing away hard-won behaviour, not replacing it. The disagreements are the working
 * list.
 *
 * ```
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 demand=64"
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 demand=64 verbose"
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 iron_nugget stick crossbow"
 * ```
 *
 * Args: `world=<id>` / `version=<v>` to pick the graph, `demand=<n>` (the shipped scorer is
 * demand-sensitive through its recipe threshold; the cost model is not, which is itself one
 * of the differences worth seeing), `verbose` to print every disagreement rather than a
 * sample, and any bare item ids to compare only those.
 */
fun main(args: Array<String>) {
    val exitCode = runBlocking {
        try {
            run(args.toList())
        } catch (e: Throwable) {
            System.err.println("cost-diagnostics failed: ${e.message}")
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
    var verbose = false
    val only = mutableListOf<String>()

    for (arg in args) {
        when {
            arg.startsWith("version=") -> version = arg.substringAfter('=')
            arg.startsWith("world=") -> worldId = arg.substringAfter('=').toIntOrNull()
            arg.startsWith("demand=") -> demand = arg.substringAfter('=').toLongOrNull() ?: demand
            arg == "verbose" -> verbose = true
            else -> only.add(if (':' in arg) arg else "minecraft:$arg")
        }
    }

    val resolvedVersion = version ?: resolveVersionForCost(worldId) ?: return 1
    val graph = when (val r = GetItemSourceGraphForVersionStep.process(resolvedVersion)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            System.err.println("No graph for version '$resolvedVersion' (${r.error}). Is it ingested?")
            return 1
        }
    }

    val model = UnitCostModel(graph, effort = EffortTable.DEFAULT)

    println("Cost model vs SelectionScorer · version $resolvedVersion · demand $demand")
    println("Sources: ${graph.getSourceCount()}, items: ${graph.getItemCount()}")

    val subjects = graph.getAllItems()
        .map { it.item }
        .filter { it !is MinecraftTag }
        .filter { graph.getSourcesForItem(it).size > 1 }
        .filter { only.isEmpty() || it.id in only }
        .sortedBy { it.id }

    var agree = 0
    val disagreements = mutableListOf<Disagreement>()
    var unreachable = 0

    for (item in subjects) {
        val shipped = ScoreDiagnostics.report(graph, item.id, demand)
            .candidates.firstOrNull() ?: continue
        val proposed = model.best(item)
        if (proposed == null) {
            unreachable++
            continue
        }
        if (proposed.getKey() == shipped.sourceKey) {
            agree++
        } else {
            disagreements += Disagreement(
                itemId = item.id,
                shippedKey = shipped.sourceKey,
                shippedMethod = shipped.method,
                shippedScore = shipped.total,
                proposedKey = proposed.getKey(),
                proposedMethod = proposed.getMethodLabel(),
                proposedCost = model.costOf(proposed, item),
                shippedCost = graph.getSourceNode(
                    shipped.sourceKey.substringBeforeLast(':'),
                    shipped.sourceKey.substringAfterLast(':')
                )?.let { model.costOf(it, item) } ?: Double.NaN,
            )
        }
    }

    val compared = agree + disagreements.size
    println()
    println("Compared $compared items with more than one source.")
    println("  agree      ${agree.pct(compared)}")
    println("  disagree   ${disagreements.size.pct(compared)}")
    if (unreachable > 0) println("  no finite cost under the new model: $unreachable (see note below)")

    // A disagreement is only interesting if the two picks cost materially different amounts.
    // Where they cost the same the models are not disagreeing about anything — they are
    // breaking a tie differently, and the shipped tie-break is as defensible as ours.
    val tie = disagreements.filter { !it.shippedCost.isNaN() && ratio(it) < 1.2 }
    val cheaper = disagreements.filter { !it.shippedCost.isNaN() && ratio(it) >= 1.2 }
    val unknown = disagreements.filter { it.shippedCost.isNaN() }
    println()
    println("Of the ${disagreements.size} disagreements:")
    println("  ${tie.size} are ties — both picks within 20% of the same cost, so only the tie-break differs")
    println("  ${cheaper.size} are claims: the new model's pick is >=1.2x cheaper than the shipped one")
    if (unknown.isNotEmpty()) println("  ${unknown.size} could not be priced on the shipped side")
    println("\n=== the ${cheaper.size} claims, biggest saving first ===")
    cheaper.sortedByDescending { ratio(it) }.take(if (verbose) cheaper.size else 25).forEach { d ->
        println(
            "  %-34s %-14s -> %-14s  %8s -> %-8s  (%.0fx)".format(
                d.itemId.substringAfter(':'), d.shippedMethod, d.proposedMethod,
                fmt(d.shippedCost), fmt(d.proposedCost), ratio(d)
            )
        )
    }

    // Grouped by the *shape* of the swap, because that is what tells a fix from a regression:
    // "chest loot -> a recipe" repeated forty times is one decision to judge, not forty.
    println("\n=== disagreements by shape (shipped -> proposed) ===")
    disagreements
        .groupingBy { "${it.shippedMethod} -> ${it.proposedMethod}" }
        .eachCount()
        .entries.sortedByDescending { it.value }
        .forEach { (shape, n) -> println("  %5d  %s".format(n, shape)) }

    println("\n=== examples ===")
    val shown = if (verbose) disagreements else disagreements
        .groupBy { "${it.shippedMethod} -> ${it.proposedMethod}" }
        .flatMap { (_, v) -> v.take(3) }
    shown.sortedBy { it.itemId }.forEach { d ->
        println("  ${d.itemId}")
        println("      shipped   %-22s score %4d   costs %s".format(d.shippedMethod, d.shippedScore, fmt(d.shippedCost)))
        println("      proposed  %-22s              costs %s".format(d.proposedMethod, fmt(d.proposedCost)))
    }

    println(
        """

        Reading this: a disagreement is only a regression if the shipped pick is genuinely
        cheaper for a player than the proposed one. The cost column says what the new model
        believes; judge it against what you would actually do in game. Where the shipped pick
        costs *more* under the new model, the new model is claiming a fix.

        Items with no finite cost are not a defect of the model: they are items whose only
        sources are circular (break what you placed) or dead ends, which the shipped model
        ranks anyway because a score always produces a number. That difference is the point —
        a cost can say "there is no way to get this", and a score cannot.
        """.trimIndent()
    )
    return 0
}

private data class Disagreement(
    val itemId: String,
    val shippedKey: String,
    val shippedMethod: String,
    val shippedScore: Int,
    val proposedKey: String,
    val proposedMethod: String,
    val proposedCost: Double,
    val shippedCost: Double,
)

/** How much cheaper the new model's pick is than the shipped one, under the new model's costs. */
private fun ratio(d: Disagreement): Double =
    if (d.proposedCost <= 0.0) Double.MAX_VALUE else d.shippedCost / d.proposedCost

private fun fmt(v: Double): String = when {
    v.isNaN() -> "?"
    v >= UnitCostModel.UNREACHABLE -> "unreachable"
    v >= 100 -> "%.0f min".format(v)
    v >= 1 -> "%.1f min".format(v)
    else -> "%.2f min".format(v)
}

private fun Int.pct(total: Int): String =
    if (total == 0) "$this" else "$this (%.1f%%)".format(100.0 * this / total)

/** Duplicated rather than shared: top-level `private` in Kotlin is file-scoped, and a
 *  read-only diagnostic is not worth widening the other one's API for. */
private suspend fun resolveVersionForCost(worldId: Int?): String? {
    if (worldId != null) {
        val resolved = (worldVersionQuery.process(worldId) as? Result.Success)?.value
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
