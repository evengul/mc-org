package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.EffortTable
import app.mcorg.engine.plan.PlanSelector
import app.mcorg.engine.plan.PlanTarget
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
    val modes = mutableSetOf<String>()
    val only = mutableListOf<String>()

    for (arg in args) {
        when {
            arg.startsWith("version=") -> version = arg.substringAfter('=')
            arg.startsWith("world=") -> worldId = arg.substringAfter('=').toIntOrNull()
            arg.startsWith("demand=") -> demand = arg.substringAfter('=').toLongOrNull() ?: demand
            arg == "verbose" -> verbose = true
            arg in AUDIT_MODES -> modes.add(arg)
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

    if (modes.isNotEmpty()) {
        if ("dist" in modes) distribution(graph, model)
        if ("audit" in modes) audit(graph, model)
        if ("shuffle" in modes) shuffle(graph, model)
        if ("why" in modes) why(graph, model, only)
        if ("gate" in modes) gate(graph, model)
        if ("placed" in modes) placed(graph, model)
        if ("converge" in modes) converge(graph, model)
        if ("selector" in modes) selectorGap(graph, model, demand, only, verbose)
        return 0
    }

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

// ---------------------------------------------------------------------------
// Audit modes (read-only), added while adversarially reviewing the sketch. Each
// answers one "is the model — or this harness — lying?" question.
// ---------------------------------------------------------------------------

private val AUDIT_MODES = setOf("dist", "audit", "shuffle", "why", "selector", "gate", "placed", "converge")

/** Local copy of the engine's internal predicate — mc-web cannot see `SelectionScorer`. */
private fun isSelfBlockLootHere(item: app.mcorg.domain.model.minecraft.MinecraftId, source: SourceNode): Boolean {
    if (source.sourceType != ResourceSource.SourceType.LootTypes.BLOCK) return false
    return source.filename.substringAfterLast('/').substringBeforeLast('.') == item.id.substringAfterLast(':')
}

/**
 * Attack on the self-block-loot gate. For every item whose own block drop is suppressed
 * because *some* recipe exists, what would breaking the block have cost, and what is the
 * model charging instead? A big gap is the gate doing the acacia_log mistake in reverse.
 */
private fun gate(graph: ItemSourceGraph, model: UnitCostModel) {
    println("\n=== items the self-block-loot gate overcharges ===")
    println("  (gate fires => own block drop suppressed; 'raw' is what breaking it would have cost)")
    val rows = mutableListOf<Triple<Double, String, String>>()
    for (node in graph.getAllItems()) {
        val item = node.item
        if (item is MinecraftTag) continue
        val sources = graph.getSourcesForItem(item)
        val constructive = sources.filter { it.sourceType.isConstructive() }
        if (constructive.isEmpty()) continue
        val self = sources.filter { isSelfBlockLootHere(item, it) }
        if (self.isEmpty()) continue
        val raw = self.minOf { s ->
            val y = graph.getExpectedYield(s, node)?.takeIf { it > 0.0 }
                ?: graph.getProducedQuantity(s, node).coerceAtLeast(1).toDouble()
            EffortTable.DEFAULT.of(s.sourceType) / y
        }
        val settled = model.cost[item.id] ?: Double.NaN
        if (settled.isNaN() || settled <= raw * 1.0000001) continue
        val via = model.best(item)?.let { "${it.sourceType.id.substringAfter(':')} ${it.filename}" } ?: "nothing"
        rows += Triple(
            if (settled >= UnitCostModel.UNREACHABLE) Double.MAX_VALUE else settled / raw,
            item.id,
            "raw %s -> charged %s, via %s; constructive siblings: %s".format(
                fmt(raw), fmt(settled), via,
                constructive.joinToString(", ") { it.sourceType.id.substringAfter(':') + " " + it.filename.substringAfterLast('/') }.take(110)
            )
        )
    }
    println("  ${rows.size} items")
    rows.sortedByDescending { it.first }.forEach { println("  %6.1fx  %-36s %s".format(it.first, it.second, it.third)) }
}

/**
 * The other half of the same hole: block loot from a block the player had to *build*, where
 * the name test cannot see it (break an ender chest for obsidian, a bookshelf for books).
 * Lists every item whose chosen source is block loot from a block that is itself craftable.
 */
private fun placed(graph: ItemSourceGraph, model: UnitCostModel) {
    println("\n=== chosen source is 'break a block that is itself crafted' ===")
    val craftable = graph.getAllItems().map { it.item }
        .filter { it !is MinecraftTag }
        .filter { i -> graph.getSourcesForItem(i).any { it.sourceType.isRecipe() } }
        .associateBy { it.id.substringAfterLast(':') }

    for (node in graph.getAllItems().sortedBy { it.itemId }) {
        val item = node.item
        if (item is MinecraftTag) continue
        val best = model.best(item) ?: continue
        if (best.sourceType != ResourceSource.SourceType.LootTypes.BLOCK) continue
        val stem = best.filename.substringAfterLast('/').substringBeforeLast('.')
        if (stem == item.id.substringAfterLast(':')) continue // already the gate's business
        val block = craftable[stem] ?: continue
        println(
            "  %-34s %s  (breaking %s, itself craftable at %s)".format(
                item.id.substringAfter(':'), fmt(model.cost[item.id] ?: Double.NaN), stem,
                fmt(model.cost[block.id] ?: Double.NaN)
            )
        )
    }
}

/** Does the relaxation actually settle, and does the pass budget change any answer? */
private fun converge(graph: ItemSourceGraph, model: UnitCostModel) {
    println("\n=== convergence ===")
    val reference = UnitCostModel(graph, maxPasses = 20000)
    println("  maxPasses=20000: passes ${reference.passesUsed}, converged=${reference.converged}")
    for (budget in listOf(4, 8, 16, 32, 64, 128, 512, 4096)) {
        val m = UnitCostModel(graph, maxPasses = budget)
        var moved = 0
        var worstRel = 0.0
        var worst = ""
        var picks = 0
        for (node in graph.getAllItems()) {
            val a = m.cost[node.item.id] ?: continue
            val b = reference.cost[node.item.id] ?: continue
            if (kotlin.math.abs(a - b) > 1e-9 * kotlin.math.max(1.0, b)) {
                moved++
                val rel = (a - b) / kotlin.math.max(1e-12, b)
                if (rel > worstRel) { worstRel = rel; worst = node.item.id }
            }
            if (node.item !is MinecraftTag && m.best(node.item)?.getKey() != reference.best(node.item)?.getKey()) picks++
        }
        println(
            "  budget %5d: passes %5d converged=%-5b  costs differing from reference %4d (worst +%.3f%% on %s), picks differing %d"
                .format(budget, m.passesUsed, m.converged, moved, 100 * worstRel, worst, picks)
        )
    }
}

/** Cost distribution plus the extremes, where modelling bugs surface as absurd numbers. */
private fun distribution(graph: ItemSourceGraph, model: UnitCostModel) {
    val priced = model.cost.filterValues { it < UnitCostModel.UNREACHABLE }
    val unreachable = model.cost.filterValues { it >= UnitCostModel.UNREACHABLE }.keys.sorted()
    println("\n=== cost distribution ===")
    println("  priced ${priced.size}, unreachable ${unreachable.size} of ${model.cost.size}")
    val buckets = listOf(0.0, 0.02, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 15.0, 60.0, 240.0, 1e6)
    for (i in 0 until buckets.size - 1) {
        val n = priced.values.count { it >= buckets[i] && it < buckets[i + 1] }
        if (n > 0) println("  %8.2f .. %-8.2f  %4d".format(buckets[i], buckets[i + 1], n))
    }
    println("\n  --- 40 cheapest ---")
    priced.entries.sortedBy { it.value }.take(40)
        .forEach { println("    %-44s %s".format(it.key, fmt(it.value))) }
    println("\n  --- 40 dearest ---")
    priced.entries.sortedByDescending { it.value }.take(40)
        .forEach { println("    %-44s %s".format(it.key, fmt(it.value))) }
    println("\n  --- unreachable, though the graph does have sources for them ---")
    unreachable.filter { id ->
        graph.getItemNodesByStringId(id).any { graph.getSourcesForItem(it.item).isNotEmpty() }
    }.forEach { id ->
        val kinds = graph.getItemNodesByStringId(id)
            .flatMap { graph.getSourcesForItem(it.item) }
            .joinToString(", ") { it.getKey() }
        println("    %-44s %s".format(id, kinds.take(150)))
    }
}

/** Convergence, fixpoint consistency between the relaxation and the public accessor, ties. */
private fun audit(graph: ItemSourceGraph, model: UnitCostModel) {
    model.cost // force the lazy relaxation
    println("\n=== relaxation audit ===")
    println("  passes used ${model.passesUsed}, converged=${model.converged}")

    var inconsistent = 0
    var tied = 0
    for (node in graph.getAllItems()) {
        val item = node.item
        if (item is MinecraftTag) continue
        val settled = model.cost[item.id] ?: continue
        val sources = graph.getSourcesForItem(item)
        if (sources.isEmpty()) continue
        val recomputed = sources.minOf { model.costOf(it, item) }
        if (kotlin.math.abs(recomputed - settled) > 1e-9 * kotlin.math.max(1.0, settled)) {
            if (inconsistent < 25) {
                println("  MISMATCH %-34s settled %s, min-over-sources %s".format(item.id, fmt(settled), fmt(recomputed)))
            }
            inconsistent++
        }
        val finite = sources.filter { model.costOf(it, item) < UnitCostModel.UNREACHABLE }
        if (finite.size > 1) {
            val lo = finite.minOf { model.costOf(it, item) }
            if (finite.count { kotlin.math.abs(model.costOf(it, item) - lo) <= 1e-12 } > 1) tied++
        }
    }
    println("  items whose settled cost != min over their own sources: $inconsistent")
    println("  items whose cheapest source is an exact tie between >=2 sources: $tied")
    println("    (best() breaks those by graph iteration order — the model declares no tie-break)")

    val allTags = graph.getAllItems().map { it.item }.filterIsInstance<MinecraftTag>()
    val tagsWithSources = allTags.filter { graph.getSourcesForItem(it).isNotEmpty() }
    println("  tag nodes that also carry producing sources: ${tagsWithSources.size}")
    tagsWithSources.take(10).forEach { println("    ${it.id}") }

    val nested = allTags.filter { tag -> tag.content.any { it is MinecraftTag } }
    println("  tags with a nested tag member: ${nested.size}")
    val missingMembers = allTags.filter { tag -> tag.content.any { m -> model.cost[m.id] == null } }
    println("  tags with a member absent from the cost map (silently unreachable): ${missingMembers.size}")
    missingMembers.take(10).forEach { tag ->
        println("    ${tag.id} -> ${tag.content.filter { model.cost[it.id] == null }.take(4).map { it.id }}")
    }
}

/** Does any answer depend on the order [UnitCostModel] happens to sweep items in? */
private fun shuffle(graph: ItemSourceGraph, model: UnitCostModel) {
    println("\n=== order sensitivity ===")
    val baseCost = model.cost
    val items = graph.getAllItems().toList()
    val baseBest = items.map { it.item }.filter { it !is MinecraftTag }
        .associate { it.id to model.best(it)?.getKey() }
    println("  base: passes ${model.passesUsed}, converged=${model.converged}")

    var costDrift = 0
    var pickDrift = 0
    val examples = mutableListOf<String>()
    repeat(5) { seed ->
        val permuted = UnitCostModel(graph, itemOrder = items.shuffled(kotlin.random.Random(seed.toLong())))
        for (node in items) {
            val a = baseCost[node.item.id] ?: continue
            val b = permuted.cost[node.item.id] ?: continue
            if (kotlin.math.abs(a - b) > 1e-9 * kotlin.math.max(1.0, a)) {
                costDrift++
                if (examples.size < 25) examples += "cost ${node.item.id}: ${fmt(a)} vs ${fmt(b)} (seed $seed)"
            }
        }
        for (node in items) {
            val item = node.item
            if (item is MinecraftTag) continue
            val a = baseBest[item.id] ?: continue
            val b = permuted.best(item)?.getKey() ?: continue
            if (a != b) {
                pickDrift++
                if (examples.size < 25) examples += "pick ${item.id}: $a vs $b (seed $seed)"
            }
        }
        println("  seed $seed: passes ${permuted.passesUsed}, converged=${permuted.converged}")
    }
    println("  cost values that moved under a permuted sweep: $costDrift")
    println("  best() picks that moved under a permuted sweep: $pickDrift")
    examples.forEach { println("    $it") }
}

/** Per-source cost breakdown for the named items — the "why is it that number" view. */
private fun why(graph: ItemSourceGraph, model: UnitCostModel, only: List<String>) {
    println("\n=== per-source costs ===")
    for (id in only) {
        val node = graph.getItemNodesByStringId(id).firstOrNull { it.item !is MinecraftTag }
            ?: graph.getItemNodesByStringId(id).firstOrNull()
        if (node == null) { println("  $id: not in graph"); continue }
        val item = node.item
        println("  $id  settled ${fmt(model.cost[id] ?: Double.NaN)}  (tag=${item is MinecraftTag})")
        if (item is MinecraftTag) {
            item.content.sortedBy { model.cost[it.id] ?: Double.MAX_VALUE }.take(8).forEach {
                println("      member %-40s %s".format(it.id, fmt(model.cost[it.id] ?: Double.NaN)))
            }
        }
        graph.getSourcesForItem(item).sortedBy { model.costOf(it, item) }.forEach { s ->
            val req = graph.getRequiredItems(s).joinToString(", ") {
                "${graph.getRequiredQuantity(s, it)}x${it.itemId.substringAfter(':')}@${fmt(model.cost[it.itemId] ?: Double.NaN)}"
            }
            println(
                "      %-14s q=%d ey=%-7s cost %-13s <- %s".format(
                    s.sourceType.id.substringAfter(':').take(14),
                    graph.getProducedQuantity(s, node),
                    graph.getExpectedYield(s, node)?.let { "%.4f".format(it) } ?: "-",
                    fmt(model.costOf(s, item)),
                    req.ifEmpty { "(terminal)" }
                )
            )
            println("          ${s.filename}")
        }
    }
}

/**
 * The harness's own baseline, checked. [ScoreDiagnostics.report].candidates.first() is
 * documented as "the scorer's favourite", not what the planner commits to — [PlanSelector]
 * applies structural feasibility rejection and its own demand propagation afterwards. This
 * runs the real selector, one target at a time, and reports how often the two differ.
 */
private fun selectorGap(
    graph: ItemSourceGraph,
    model: UnitCostModel,
    demand: Long,
    only: List<String>,
    verbose: Boolean,
) {
    println("\n=== ScoreDiagnostics.first() vs what PlanSelector actually picks ===")
    val subjects = graph.getAllItems().map { it.item }
        .filter { it !is MinecraftTag }
        .filter { graph.getSourcesForItem(it).size > 1 }
        .filter { only.isEmpty() || it.id in only }
        .sortedBy { it.id }

    var same = 0
    var differ = 0
    var noPick = 0
    var flipsVerdict = 0
    val rows = mutableListOf<String>()
    for (item in subjects) {
        val scorerPick = ScoreDiagnostics.report(graph, item.id, demand).candidates.firstOrNull()?.sourceKey ?: continue
        val dag = PlanSelector.select(graph, listOf(PlanTarget(item, demand)))
        val selected = dag.nodes[dag.roots[item.id] ?: item.id]
        val realPick = selected?.source?.getKey()
        if (realPick == null) {
            noPick++
            rows += "  NO PICK   %-34s status=%s (scorer said %s)".format(item.id, selected?.status, scorerPick)
            continue
        }
        if (realPick == scorerPick) { same++; continue }
        differ++
        val proposed = model.best(item)?.getKey()
        val verdictFlip = (proposed == realPick) != (proposed == scorerPick)
        if (verdictFlip) flipsVerdict++
        rows += "  %-30s scorer=%-44s selector=%-44s cost=%s%s".format(
            item.id.substringAfter(':'), scorerPick, realPick, proposed,
            if (verdictFlip) "   <-- harness verdict FLIPS" else ""
        )
    }
    println("  compared ${same + differ + noPick}: same $same, differ $differ, selector picked nothing $noPick")
    println("  disagreements whose agree/disagree verdict FLIPS with the real selector as baseline: $flipsVerdict")
    rows.take(if (verbose) rows.size else 80).forEach { println(it) }
}
