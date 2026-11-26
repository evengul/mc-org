package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceSourceType
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.SupplySource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * One mode's rate for one item on a farm project: a `project_productions` row with its mode.
 *
 * @param modeId null for a farm without modes, whose rows are one implicit mode.
 */
data class FarmModeRate(
    val projectId: Int,
    val modeId: Int?,
    val modeName: String?,
    val modePosition: Int,
    val itemId: String,
    val ratePerHour: Int,
)

/** A plan line a farm supplies: what the plan needs of the item and how much is logged against it. */
data class SuppliedLine(
    val itemId: String,
    val itemName: String,
    val quantity: Long,
    val logged: Long,
) {
    val left: Long get() = (quantity - logged).coerceAtLeast(0)
    val isDone: Boolean get() = left == 0L

    /** Partly logged: someone is emptying the farm for this line right now. */
    val isInProgress: Boolean get() = logged > 0 && !isDone
}

/**
 * One item in a mode's run.
 *
 * @param ratePerHour the mode's rate for it; 0 is unmeasured, which gives no time rather than an
 *   infinite one.
 */
data class ItemRun(val line: SuppliedLine, val ratePerHour: Int) {
    val isMeasured: Boolean get() = ratePerHour > 0
    val hoursLeft: Double? get() = if (isMeasured) line.left.toDouble() / ratePerHour else null
    val hoursNeeded: Double? get() = if (isMeasured) line.quantity.toDouble() / ratePerHour else null
}

/**
 * What one plan asks of one mode. The mode runs as long as its longest-needed item: items it makes
 * together share the time rather than adding up.
 */
data class ModeRun(
    val modeId: Int?,
    val modeName: String?,
    val modePosition: Int,
    val items: List<ItemRun>,
) {
    private val open: List<ItemRun> get() = items.filterNot { it.line.isDone }

    val isDone: Boolean get() = items.all { it.line.isDone }

    /** The open item that takes longest, which is what the mode's time is. */
    val setBy: ItemRun? get() = open.filter { it.isMeasured }.maxByOrNull { it.hoursLeft!! }

    val hoursLeft: Double? get() = setBy?.hoursLeft

    val hoursNeeded: Double? get() = items.mapNotNull { it.hoursNeeded }.maxOrNull()

    /** A few seconds of running: frame 2a groups these as "under a minute each". */
    val isToken: Boolean get() = hoursLeft?.let { it < FarmRuns.TOKEN_HOURS } ?: false
}

/**
 * What one plan asks of one farm (MCO-603): each mode it needs, and for how long.
 *
 * A farm runs one mode at a time, so its time is the sum of its modes' times. [setBy] is the item
 * that sets the longest mode — the one a heading names, "set by Iron Nugget, 296,800 at 1,000/hr".
 * It is not always the biggest line: Copper Library's Witch hut farm is set by 384 Glass Bottle at
 * 785/hr, not by 599 Stick at 1,580/hr.
 */
data class FarmRun(
    val projectId: Int,
    val projectName: String,
    val modes: List<ModeRun>,
    /** How many modes the farm has in all, asked for or not; 0 for a farm without modes. */
    val farmModeCount: Int = 0,
) {
    private val openModes: List<ModeRun> get() = modes.filterNot { it.isDone }

    val isDone: Boolean get() = modes.all { it.isDone }

    /** A line of it is partly logged, so the player is at this farm now. */
    val isInProgress: Boolean get() = modes.any { mode -> mode.items.any { it.line.isInProgress } }

    val items: List<ItemRun> get() = modes.flatMap { it.items }

    val setBy: ItemRun? get() = openModes.filter { it.hoursLeft != null }.maxByOrNull { it.hoursLeft!! }?.setBy

    /** Time left over the modes still needed; null when no open item has a measured rate. */
    val hoursLeft: Double? get() = openModes.mapNotNull { it.hoursLeft }.takeIf { it.isNotEmpty() }?.sum()

    /** The whole run as if nothing were logged, for "~13 min left of ~45 min". */
    val hoursNeeded: Double? get() = modes.mapNotNull { it.hoursNeeded }.takeIf { it.isNotEmpty() }?.sum()

    /** Open lines this farm supplies without a measured rate: they still count, only the time is missing. */
    val unmeasured: List<ItemRun> get() = modes.flatMap { it.items }.filter { !it.isMeasured && !it.line.isDone }
}

object FarmRuns {

    /** Under a minute of running. */
    const val TOKEN_HOURS: Double = 1.0 / 60

    /**
     * Each farm's run for one plan. Pure.
     *
     * @param lines the plan's farm-supplied lines; a line no farm in [producers] makes is left out.
     * @param producers the farm that supplies each item, from [ProjectSupply.producers] over the
     *   rows the plan was folded from, so the run names the same farm as the plan.
     * @param rates every mode's rates for those farms; see [assignModes] for which mode an item goes to.
     */
    fun derive(
        lines: List<SuppliedLine>,
        producers: Map<String, FarmSupplyRow>,
        rates: List<FarmModeRate>,
    ): List<FarmRun> {
        val ratesByFarm = rates.groupBy { it.projectId }
        return lines
            .mapNotNull { line -> producers[line.itemId]?.let { it to line } }
            .groupBy { (producer, _) -> producer.projectId }
            .map { (projectId, entries) ->
                // A farm whose rows carry no mode at all is still one run, at the rate supply read.
                val supplyRates = entries.associate { (producer, line) -> line.itemId to producer.ratePerHour }
                val farmRates = ratesByFarm[projectId].orEmpty()
                val modes = assignModes(entries.map { it.second }, farmRates, projectId, supplyRates)
                FarmRun(
                    projectId = projectId,
                    projectName = entries.first().first.projectName,
                    modes = modes,
                    farmModeCount = farmRates.mapNotNull { it.modeId }.distinct().size,
                )
            }
            .sortedBy { it.projectName }
    }

    /**
     * Which mode each of one farm's lines comes from (decided 2026-10-09, on MCO-603's review).
     *
     * An item rides along on a mode the plan already runs, rather than starting a faster one: the
     * sticks a tree farm's Oak Mode makes come out of the hour the Oak Log needs anyway, and switching
     * to Jungle Mode for them would add its own run for nothing. So lines are taken longest first,
     * each at its fastest mode's time, and each goes where it adds least: into a mode already chosen
     * (stretching it only past its current time), or into its own fastest mode as a new run. A tie
     * stays in a mode already chosen, so the plan asks for as few switches as it can.
     *
     * Greedy, and not always the least total time — a farm has a handful of modes, and the case it
     * gets wrong needs a large line that a slower mode could also absorb. A line no mode has a
     * measured rate for goes to a chosen mode that makes it, else to the first mode listed.
     */
    private fun assignModes(
        lines: List<SuppliedLine>,
        farmRates: List<FarmModeRate>,
        projectId: Int,
        supplyRates: Map<String, Int>,
    ): List<ModeRun> {
        val byItem = farmRates.groupBy { it.itemId }
        fun optionsFor(line: SuppliedLine): List<FarmModeRate> =
            byItem[line.itemId]
                ?.sortedWith(compareByDescending<FarmModeRate> { it.ratePerHour }.thenBy { it.modePosition })
                ?: listOf(FarmModeRate(projectId, null, null, 0, line.itemId, supplyRates[line.itemId] ?: 0))
        fun hours(line: SuppliedLine, rate: FarmModeRate): Double? =
            if (rate.ratePerHour > 0) line.left.toDouble() / rate.ratePerHour else null

        val chosen = linkedMapOf<Int?, MutableList<Pair<SuppliedLine, FarmModeRate>>>()
        fun modeHours(modeId: Int?): Double =
            chosen[modeId].orEmpty().mapNotNull { (line, rate) -> hours(line, rate) }.maxOrNull() ?: 0.0

        val longestFirst = lines.sortedByDescending { line -> hours(line, optionsFor(line).first()) ?: -1.0 }
        for (line in longestFirst) {
            val options = optionsFor(line)
            val fastest = options.first()
            val alreadyRunning = options.filter { it.modeId in chosen }
            val pick = if (hours(line, fastest) == null) {
                alreadyRunning.firstOrNull() ?: fastest
            } else {
                val stretch = alreadyRunning
                    .filter { it.ratePerHour > 0 }
                    .map { rate -> rate to ((hours(line, rate)!! - modeHours(rate.modeId)).coerceAtLeast(0.0)) }
                    .minByOrNull { it.second }
                val ownRun = if (fastest.modeId in chosen) {
                    (hours(line, fastest)!! - modeHours(fastest.modeId)).coerceAtLeast(0.0)
                } else {
                    hours(line, fastest)!!
                }
                if (stretch != null && stretch.second <= ownRun) stretch.first else fastest
            }
            chosen.getOrPut(pick.modeId) { mutableListOf() }.add(line to pick)
        }

        return chosen.map { (modeId, entries) ->
            val mode = entries.first().second
            ModeRun(
                modeId = modeId,
                modeName = mode.modeName,
                modePosition = mode.modePosition,
                items = entries
                    .map { (line, rate) -> ItemRun(line, rate.ratePerHour) }
                    .sortedByDescending { it.line.quantity },
            )
        }.sortedBy { it.modePosition }
    }

    /**
     * Each farm's run for the plan the project page shows, loading what [derive] needs.
     *
     * The world's running farms only: that plan is derived without assumed farms, and a line a
     * `manual` pick or a project link took from farm supply is not a farm-supplied line, so it is
     * never in [lines]. A plan derived as if unbuilt farms were finished (the roadmap's promised
     * split) is not one this reads.
     *
     * @param lines the plan's farm-supplied lines, with what is logged against each.
     */
    suspend fun load(worldId: Int, projectId: Int, lines: List<SuppliedLine>): Result<AppFailure, List<FarmRun>> {
        if (lines.isEmpty()) return Result.success(emptyList())
        val farms = when (
            val r = GetWorldFarmSuppliesStep.process(WorldFarmSuppliesInput(worldId = worldId, excludeProjectId = projectId))
        ) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val needed = lines.map { it.itemId }.toSet()
        val producers = ProjectSupply.producers(farms.filter { it.itemId in needed })
        if (producers.isEmpty()) return Result.success(emptyList())
        val rates = when (val r = GetFarmModeRatesStep.process(producers.values.map { it.projectId }.toSet())) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return Result.success(derive(lines, producers, rates))
    }

    /**
     * The plan's farm-supplied lines, as [load] takes them. A line a linked project supplies is
     * not a farm's, and stays out.
     *
     * A target logged in full has already left the plan (`GenerateGatheringPlanStep` plans what is
     * left of a target), so only a derived line can come back here as done.
     */
    fun suppliedLines(plan: GatheringPlan, progress: Map<String, Int>): List<SuppliedLine> =
        plan.activityList
            .filter { it.status == PlanNodeStatus.SUPPLIED && it.supply is SupplySource.Farm }
            .map { activity ->
                SuppliedLine(
                    itemId = activity.item.id,
                    itemName = activity.item.name,
                    quantity = activity.quantity,
                    logged = (progress[activity.item.id] ?: 0).toLong().coerceIn(0, activity.quantity),
                )
            }

    /**
     * Each farm's run for the plan a page shows, or none.
     *
     * None rather than a failed page: without runs, Collect draws its lines the way it did before
     * MCO-604, which is still a plan you can work from.
     *
     * @param finished a target line just logged in full. It has left the plan, but its farm's
     *   heading still has to say the farm is done with it, so it is counted as a done line.
     */
    suspend fun forPlan(
        worldId: Int,
        projectId: Int,
        plan: GatheringPlan?,
        progress: Map<String, Int>,
        finished: SuppliedLine? = null,
    ): List<FarmRun> {
        if (plan == null && finished == null) return emptyList()
        val lines = plan?.let { suppliedLines(it, progress) }.orEmpty() + listOfNotNull(finished)
        return load(worldId, projectId, lines).getOrNull() ?: emptyList()
    }

    /**
     * The done line to pass [forPlan] for a target logged in full, which has left the plan; null
     * when the target was not the farms' to supply.
     *
     * The plan's own rule decides (`ProjectSupply.fold`): a `manual` source opts the item out of
     * farm supply, and a linked project supplies it instead. Without this a manual target that
     * some farm happens to make would be counted into that farm's run.
     */
    suspend fun finishedTarget(projectId: Int, itemId: String, itemName: String, required: Long): SuppliedLine? {
        val row = GetAllResourceGatheringItemsStep.process(projectId).getOrNull()
            ?.firstOrNull { it.itemId == itemId && !it.ignored }
            ?: return null
        if (row.sourceType == ResourceSourceType.MANUAL || row.solvedByProject != null) return null
        return SuppliedLine(itemId, itemName, required, required)
    }
}

/** Every mode's rates for the given farm projects, with each mode's name and position. */
val GetFarmModeRatesStep = DatabaseSteps.query<Set<Int>, List<FarmModeRate>>(
    sql = SafeSQL.select("""
                SELECT
                    pp.project_id,
                    pp.mode_id,
                    m.name AS mode_name,
                    COALESCE(m.position, 0) AS mode_position,
                    pp.item_id,
                    pp.rate_per_hour
                FROM project_productions pp
                LEFT JOIN project_production_modes m ON m.id = pp.mode_id
                WHERE pp.project_id = ANY(?)
                ORDER BY pp.project_id, mode_position, pp.mode_id, pp.item_id
            """),
    parameterSetter = { statement, projectIds ->
        statement.setArray(1, statement.connection.createArrayOf("integer", projectIds.toTypedArray()))
    },
    resultMapper = { rs ->
        buildList {
            while (rs.next()) {
                add(
                    FarmModeRate(
                        projectId = rs.getInt("project_id"),
                        modeId = rs.getInt("mode_id").takeUnless { rs.wasNull() },
                        modeName = rs.getString("mode_name"),
                        modePosition = rs.getInt("mode_position"),
                        itemId = rs.getString("item_id"),
                        ratePerHour = rs.getInt("rate_per_hour"),
                    )
                )
            }
        }
    }
)
