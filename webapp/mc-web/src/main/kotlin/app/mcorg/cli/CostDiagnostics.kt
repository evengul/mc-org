package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource.SourceType
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
 *
 * # calibration: move every effort value across its range and see what each one decides
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 projects=43 sweep"
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 sweep=chest verbose"
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 sweep=chest values=6,8,12"
 *
 * # try a table by hand, or ask who a source type actually wins
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 set=chest:30 picks=chest"
 * mvn -pl mc-web exec:java@cost-diagnostics -Dexec.args="world=3 table=sketch"
 * ```
 *
 * Args: `world=<id>` / `version=<v>` to pick the graph, `demand=<n>` (the shipped scorer is
 * demand-sensitive through its recipe threshold; the cost model is not, which is itself one
 * of the differences worth seeing), `verbose` to print every disagreement rather than a
 * sample, and any bare item ids to compare only those. For calibration: `sweep[=<group>]`
 * with optional `values=`, `set=<type>:<minutes>` to override the table without rebuilding,
 * `table=sketch|calibrated`, `picks=<type>` to list what a source type wins and by how much,
 * and `projects=<ids>` to carry a real project's item set through every row — the whole-graph
 * agreement rate can improve while the plan someone is actually building gets worse.
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
    var grain = false
    var sweep = false
    var sweepFilter: String? = null
    var picksOf: String? = null
    var customValues: List<Double>? = null
    val overrides = mutableListOf<Pair<String, Double>>()
    var table = EffortTable.DEFAULT
    var tableName = "calibrated"
    val projectIds = mutableListOf<Int>()
    val only = mutableListOf<String>()

    for (arg in args) {
        when {
            arg.startsWith("version=") -> version = arg.substringAfter('=')
            arg.startsWith("world=") -> worldId = arg.substringAfter('=').toIntOrNull()
            arg.startsWith("demand=") -> demand = arg.substringAfter('=').toLongOrNull() ?: demand
            arg == "verbose" -> verbose = true
            arg == "grain" -> grain = true
            arg == "sweep" -> sweep = true
            arg.startsWith("sweep=") -> { sweep = true; sweepFilter = arg.substringAfter('=') }
            arg.startsWith("picks=") -> picksOf = arg.substringAfter('=')
            arg.startsWith("set=") -> {
                val (t, v) = arg.substringAfter('=').split(':').let { it[0] to it.getOrNull(1)?.toDoubleOrNull() }
                if (v == null) { System.err.println("set= wants <type>:<minutes>, got '$arg'"); return 1 }
                overrides += t to v
            }
            arg.startsWith("values=") ->
                customValues = arg.substringAfter('=').split(',').mapNotNull { it.trim().toDoubleOrNull() }
            arg.startsWith("projects=") ->
                projectIds += arg.substringAfter('=').split(',').mapNotNull { it.trim().toIntOrNull() }
            arg.startsWith("table=") -> {
                tableName = arg.substringAfter('=')
                table = when (tableName) {
                    "sketch" -> EffortTable.SKETCH
                    "calibrated", "default" -> EffortTable.DEFAULT
                    else -> { System.err.println("Unknown table '$tableName'"); return 1 }
                }
            }
            else -> only.add(if (':' in arg) arg else "minecraft:$arg")
        }
    }

    // `set=chest:30 set=trade:8` — try a table by hand without editing and reinstalling the
    // engine, which is the loop this whole calibration is made of.
    for ((typeFilter, minutes) in overrides) {
        val matches = SourceType.all().filter { it.id.contains(typeFilter) }
        if (matches.isEmpty()) {
            System.err.println("No source type matches '$typeFilter'")
            return 1
        }
        for (type in matches) table = table.with(type, minutes)
        tableName = "$tableName+${typeFilter}=$minutes"
    }

    val resolvedVersion = version ?: resolveVersionForCost(worldId) ?: return 1
    val graph = when (val r = GetItemSourceGraphForVersionStep.process(resolvedVersion)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            System.err.println("No graph for version '$resolvedVersion' (${r.error}). Is it ingested?")
            return 1
        }
    }

    val model = UnitCostModel(graph, effort = table)

    println("Cost model ($tableName) vs SelectionScorer · version $resolvedVersion · demand $demand")
    println("Sources: ${graph.getSourceCount()}, items: ${graph.getItemCount()}")

    val subjects = graph.getAllItems()
        .map { it.item }
        .filter { it !is MinecraftTag }
        .filter { graph.getSourcesForItem(it).size > 1 }
        .filter { only.isEmpty() || it.id in only }
        .sortedBy { it.id }
    // `grain`: what did per-source effort actually change? Not the net agreement figure -- the
    // items. A net of -7 is equally consistent with seven items moving and with twenty-seven
    // moving one way while twenty moved the other, and those are different facts.
    if (grain) {
        val coarse = UnitCostModel(graph, effort = model.effortTable.typeOnly())
        val moved = subjects.mapNotNull { item ->
            val a = coarse.best(item)?.getKey()
            val b = model.best(item)?.getKey()
            if (a != null && b != null && a != b) Triple(item.id, a, b) else null
        }
        println("\nper-source effort moved ${moved.size} of ${subjects.size} picks")
        moved.sortedBy { it.first }.forEach { (id, a, b) ->
            println("  %-34s %s".format(id.substringAfter(':'), "$a  ->  $b"))
        }
        return 0
    }

    if (sweep) {
        val shippedPicks = subjects.associate { item ->
            item.id to (ScoreDiagnostics.report(graph, item.id, demand).candidates.firstOrNull()?.sourceKey ?: "")
        }
        val projects = projectIds.associateWith { loadProjectItems(it) }
            .mapValues { (_, ids) -> subjects.map { it.id }.filter { it in ids }.toSet() }
            .filterValues { it.isNotEmpty() }
        runSweep(graph, subjects, shippedPicks, projects, sweepFilter, customValues, table, verbose)
        return 0
    }

    if (picksOf != null) {
        // Which items does this source type actually win, and by how much over the runner-up?
        // "Chest is a last resort" is a claim about this list, not about the constant.
        println()
        println("Items whose cheapest route is a source matching '$picksOf' (margin = next-best / this):")
        var n = 0
        for (item in subjects) {
            val pick = model.best(item) ?: continue
            if (!pick.sourceType.id.contains(picksOf)) continue
            val mine = model.costOf(pick, item)
            val runnerUp = graph.getSourcesForItem(item)
                .filter { it != pick }
                .minOfOrNull { model.costOf(it, item) } ?: UnitCostModel.UNREACHABLE
            n++
            println(
                "  %-34s %-16s %8s   next %s".format(
                    item.id.substringAfter(':'), pick.getMethodLabel(), fmt(mine),
                    if (runnerUp >= UnitCostModel.UNREACHABLE) "only route" else fmt(runnerUp)
                )
            )
        }
        println("  $n items")
        return 0
    }

    var agree = 0
    val disagreements = mutableListOf<Disagreement>()
    var unreachable = 0

    // The baseline is what PlanSelector actually commits to, not what the scorer ranks first.
    // Those differ: the selector rejects candidates structurally before scoring ever runs, and
    // ScoreDiagnostics says so in its own file ("the scorer's favourite, not a guarantee the
    // planner committed to it"). Measured against 1.21.4 they disagree on 20 of 992 items -- the
    // 19 armour-trim duplication recipes and wheat -- and on every one of those the scorer's
    // favourite is a derivation the planner would never emit. Reading the ranking as the baseline
    // scored those as agreement and hid 19 regressions.
    fun shippedPick(item: MinecraftId): String? =
        PlanSelector.select(graph, listOf(PlanTarget(item, demand))).nodes[item.id]?.source?.getKey()

    var selectorDifferedFromScorer = 0
    for (item in subjects) {
        val shippedKey = shippedPick(item) ?: continue
        if (ScoreDiagnostics.report(graph, item.id, demand).candidates.firstOrNull()?.sourceKey != shippedKey) {
            selectorDifferedFromScorer++
        }
        val shipped = ScoreDiagnostics.report(graph, item.id, demand)
            .candidates.firstOrNull { it.sourceKey == shippedKey }
            ?: ScoreDiagnostics.report(graph, item.id, demand).candidates.firstOrNull()
            ?: continue
        val proposed = model.best(item)
        if (proposed == null) {
            unreachable++
            continue
        }
        if (proposed.getKey() == shippedKey) {
            agree++
        } else {
            disagreements += Disagreement(
                itemId = item.id,
                shippedKey = shippedKey,
                shippedMethod = shipped.method,
                shippedScore = shipped.total,
                proposedKey = proposed.getKey(),
                proposedMethod = proposed.getMethodLabel(),
                proposedCost = model.costOf(proposed, item),
                shippedCost = graph.getSourceNode(
                    shippedKey.substringBeforeLast(':'),
                    shippedKey.substringAfterLast(':')
                )?.let { model.costOf(it, item) } ?: Double.NaN,
            )
        }
    }

    val compared = agree + disagreements.size
    println()
    println("Compared $compared items with more than one source.")
    println("  baseline check: PlanSelector.select() differs from the scorer's top-ranked candidate on $selectorDifferedFromScorer items")
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
// Calibration sweep
//
// `mc-engine/CLAUDE.md` asks for the same discipline the shipped constants got: move each
// number across its plausible range and record where behaviour changes, rather than asserting
// a value is right because the suite is green. Doing that one CLI run per value would be one
// Neon round trip and one JVM start per value, so the sweep loads the graph and the shipped
// picks *once* and then rebuilds only the cost model — sweeping every entry then costs about
// as much as a single comparison run.
// ---------------------------------------------------------------------------

private typealias Picks = Map<String, SourceNode>

/**
 * The selections that must survive any calibration — the ones a player recognises and the
 * ones an issue was filed about. `iron_nugget` is MCO-320's acceptance criterion; the rest
 * were fixes the sketch produced on its first run against real data.
 */
private data class KnownGood(val name: String, val items: List<String>, val method: String)

private val WOOL_COLOURS = listOf(
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
)

private val KNOWN_GOOD = listOf(
    KnownGood("iron_nugget", listOf("minecraft:iron_nugget"), "Crafting"),
    KnownGood("wool", WOOL_COLOURS.map { "minecraft:${it}_wool" }, "Shearing"),
    KnownGood("bowl", listOf("minecraft:bowl"), "Crafting"),
    KnownGood("fire_charge", listOf("minecraft:fire_charge"), "Crafting"),
    KnownGood("susp_stew", listOf("minecraft:suspicious_stew"), "Crafting"),
    KnownGood("copper_ingot", listOf("minecraft:copper_ingot"), "Blasting"),
)

private data class SweepGroup(val label: String, val types: List<SourceType>, val values: List<Double>)

private val CRAFTING_TYPES = listOf(
    SourceType.RecipeTypes.CRAFTING_SHAPED, SourceType.RecipeTypes.CRAFTING_SHAPELESS,
    SourceType.RecipeTypes.CRAFTING_TRANSMUTE, SourceType.RecipeTypes.CRAFTING_IMBUE,
)

private val TRADE_PROFESSIONS = listOf(
    SourceType.TradeTypes.ARMORER, SourceType.TradeTypes.BUTCHER, SourceType.TradeTypes.CARTOGRAPHER,
    SourceType.TradeTypes.CLERIC, SourceType.TradeTypes.FARMER, SourceType.TradeTypes.FISHERMAN,
    SourceType.TradeTypes.FLETCHER, SourceType.TradeTypes.LEATHERWORKER, SourceType.TradeTypes.LIBRARIAN,
    SourceType.TradeTypes.MASON, SourceType.TradeTypes.SHEPHERD, SourceType.TradeTypes.SMITH,
    SourceType.TradeTypes.TOOLSMITH, SourceType.TradeTypes.WEAPONSMITH,
)

private val SWEEP_GROUPS: List<SweepGroup> = listOf(
    SweepGroup("crafting", CRAFTING_TYPES, listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0)),
    SweepGroup("stonecutting", listOf(SourceType.RecipeTypes.STONECUTTING), listOf(0.01, 0.03, 0.05, 0.06, 0.1, 0.2, 0.5, 1.0)),
    SweepGroup("smithing", listOf(SourceType.RecipeTypes.SMITHING_TRANSFORM), listOf(0.05, 0.1, 0.2, 0.5, 1.0, 3.0)),
    SweepGroup("smelting", listOf(SourceType.RecipeTypes.SMELTING), listOf(0.02, 0.05, 0.1, 0.17, 0.3, 0.5, 1.0, 2.0)),
    SweepGroup("blasting", listOf(SourceType.RecipeTypes.BLASTING), listOf(0.02, 0.05, 0.08, 0.12, 0.17, 0.3, 0.5, 1.0)),
    SweepGroup("smoking", listOf(SourceType.RecipeTypes.SMOKING), listOf(0.02, 0.05, 0.08, 0.12, 0.17, 0.3, 0.5, 1.0)),
    SweepGroup("campfire", listOf(SourceType.RecipeTypes.CAMPFIRE_COOKING), listOf(0.1, 0.25, 0.5, 1.0, 2.0, 5.0)),
    SweepGroup("block", listOf(SourceType.LootTypes.BLOCK), listOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0)),
    SweepGroup("block_interact", listOf(SourceType.LootTypes.BLOCK_INTERACT), listOf(0.01, 0.05, 0.1, 0.3, 0.5, 1.0, 2.0)),
    SweepGroup("collect", listOf(SourceType.MechanicTypes.COLLECT), listOf(0.01, 0.05, 0.1, 0.3, 0.5, 1.0, 2.0)),
    SweepGroup("in_world_transform", listOf(SourceType.MechanicTypes.IN_WORLD_TRANSFORM), listOf(0.02, 0.05, 0.1, 0.3, 0.5, 1.0, 2.0)),
    SweepGroup("entity", listOf(SourceType.LootTypes.ENTITY), listOf(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)),
    SweepGroup("entity_interact", listOf(SourceType.LootTypes.ENTITY_INTERACT), listOf(0.05, 0.1, 0.3, 0.5, 1.0, 2.0, 5.0)),
    SweepGroup("shearing", listOf(SourceType.LootTypes.SHEARING), listOf(0.02, 0.05, 0.1, 0.2, 0.4, 0.6, 1.0, 2.0)),
    SweepGroup("chest", listOf(SourceType.LootTypes.CHEST), listOf(0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 15.0, 20.0, 30.0, 60.0, 120.0)),
    SweepGroup("archaeology", listOf(SourceType.LootTypes.ARCHAEOLOGY), listOf(1.0, 5.0, 10.0, 20.0, 40.0, 90.0)),
    SweepGroup("equipment", listOf(SourceType.LootTypes.EQUIPMENT), listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 60.0)),
    SweepGroup("gift", listOf(SourceType.LootTypes.GIFT), listOf(1.0, 5.0, 10.0, 20.0, 30.0, 60.0, 120.0)),
    SweepGroup("fishing", listOf(SourceType.LootTypes.FISHING), listOf(0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0)),
    SweepGroup("barter", listOf(SourceType.LootTypes.BARTER), listOf(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)),
    SweepGroup("trades", TRADE_PROFESSIONS, listOf(0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 15.0, 30.0)),
    SweepGroup("wandering_trader", listOf(SourceType.TradeTypes.WANDERING_TRADER), listOf(0.5, 1.0, 3.0, 5.0, 8.0, 15.0, 30.0, 60.0)),
)

private fun pickAll(graph: ItemSourceGraph, subjects: List<MinecraftId>, table: EffortTable): Pair<UnitCostModel, Picks> {
    val model = UnitCostModel(graph, effort = table)
    val picks = LinkedHashMap<String, SourceNode>()
    for (item in subjects) model.best(item)?.let { picks[item.id] = it }
    return model to picks
}

private fun nodeFor(graph: ItemSourceGraph, key: String): SourceNode? =
    if (key.isEmpty()) null
    else graph.getSourceNode(key.substringBeforeLast(':'), key.substringAfterLast(':'))

private fun runSweep(
    graph: ItemSourceGraph,
    subjects: List<MinecraftId>,
    shipped: Map<String, String>,
    projects: Map<Int, Set<String>>,
    filter: String?,
    customValues: List<Double>?,
    baseTable: EffortTable,
    detail: Boolean,
) {
    val (_, basePicks) = pickAll(graph, subjects, baseTable)

    println()
    println("Sweeping ${subjects.size} multi-source items. Columns:")
    println("  agree   selections matching the shipped scorer")
    println("  tie     disagreements where both picks cost the same — only the tie-break differs")
    println("  moved   selections that differ from the current table's own picks")
    println("  chest   items whose cheapest route is structure loot")
    println("  trade   items whose cheapest route is a villager or wandering trade")
    println("  fixes   known-good selections lost at this value (ok = all held)")
    projects.forEach { (id, ids) -> println("  p$id     agreement over project $id's ${ids.size} multi-source items") }

    val groups = SWEEP_GROUPS.filter { g ->
        filter == null || g.label.contains(filter) || g.types.any { it.id.contains(filter) }
    }
    if (groups.isEmpty()) {
        System.err.println("No sweep group matches '$filter'. Known: ${SWEEP_GROUPS.joinToString { it.label }}")
        return
    }

    for (group in groups) {
        val current = baseTable.of(group.types.first())
        println()
        println("=== ${group.label}  (current ${fmtValue(current)} min/attempt) ===")
        var previous: Picks? = null
        for (value in (customValues ?: group.values).sorted()) {
            var table = baseTable
            for (type in group.types) table = table.with(type, value)
            val (model, picks) = pickAll(graph, subjects, table)
            val label = fmtValue(value) + if (value == current) " *" else "  "
            println(sweepRow(label, graph, subjects, shipped, projects, basePicks, model, picks))
            // The decisions that actually turn on this number, named. A row that reports
            // "moved 3" without saying which three is a number you cannot argue with.
            if (detail && previous != null) {
                val changed = subjects.filter { picks[it.id]?.getKey() != previous!![it.id]?.getKey() }
                changed.take(24).forEach { item ->
                    println(
                        "        %-30s %s -> %s".format(
                            item.id.substringAfter(':'),
                            previous!![item.id]?.getMethodLabel() ?: "none",
                            picks[item.id]?.getMethodLabel() ?: "none",
                        )
                    )
                }
                if (changed.size > 24) println("        ... and ${changed.size - 24} more")
            }
            previous = picks
        }
    }
}

private fun sweepRow(
    label: String,
    graph: ItemSourceGraph,
    subjects: List<MinecraftId>,
    shipped: Map<String, String>,
    projects: Map<Int, Set<String>>,
    base: Picks,
    model: UnitCostModel,
    picks: Picks,
): String {
    val agree = subjects.count { picks[it.id]?.getKey() == shipped[it.id] }
    val moved = subjects.count { picks[it.id]?.getKey() != base[it.id]?.getKey() }
    val chest = picks.values.count { it.sourceType == SourceType.LootTypes.CHEST }
    val trade = picks.values.count { it.sourceType.isTrade() }

    // A disagreement where both picks cost the same is not a disagreement about cost — the
    // two models are breaking a tie differently. Counted separately so a sweep cannot look
    // like it moved a decision when all it did was nudge a tie one way.
    val tied = subjects.count { item ->
        val mine = picks[item.id] ?: return@count false
        if (mine.getKey() == shipped[item.id]) return@count false
        val theirs = nodeFor(graph, shipped[item.id] ?: "") ?: return@count false
        val a = model.costOf(mine, item)
        val b = model.costOf(theirs, item)
        b < UnitCostModel.UNREACHABLE && kotlin.math.abs(a - b) <= 1e-9 + 1e-6 * kotlin.math.max(a, b)
    }

    val broken = KNOWN_GOOD.filter { good ->
        good.items.any { id -> picks[id]?.getMethodLabel()?.let { it != good.method } == true }
    }.joinToString(",") { it.name }

    val projectCols = projects.entries.joinToString("  ") { (id, ids) ->
        "p$id %3d/%3d".format(ids.count { picks[it]?.getKey() == shipped[it] }, ids.size)
    }

    return "  %-8s agree %4d (%5.1f%%)  tie %3d  moved %4d  chest %3d  trade %3d  %s  fixes %s".format(
        label, agree, 100.0 * agree / subjects.size, tied, moved, chest, trade, projectCols,
        if (broken.isEmpty()) "ok" else broken
    )
}

private fun fmtValue(v: Double): String = if (v >= 1) "%.0f".format(v) else "%.2f".format(v)

private suspend fun loadProjectItems(projectId: Int): Set<String> =
    (projectItemsQuery.process(projectId) as? Result.Success)?.value?.toSet().orEmpty()

private val projectItemsQuery = DatabaseSteps.query<Int, List<String>>(
    sql = SafeSQL.select("SELECT DISTINCT item_id FROM resource_gathering WHERE project_id = ?"),
    parameterSetter = { ps, id -> ps.setInt(1, id) },
    resultMapper = { rs -> buildList { while (rs.next()) add(rs.getString("item_id")) } }
)
