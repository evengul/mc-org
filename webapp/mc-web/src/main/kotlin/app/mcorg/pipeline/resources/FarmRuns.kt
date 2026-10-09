package app.mcorg.pipeline.resources

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
) {
    private val openModes: List<ModeRun> get() = modes.filterNot { it.isDone }

    val isDone: Boolean get() = modes.all { it.isDone }

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
     * @param rates every mode's rates for those farms. Within a farm, an item goes to its best-rate
     *   mode; between equal rates, to the mode listed first.
     */
    fun derive(
        lines: List<SuppliedLine>,
        producers: Map<String, FarmSupplyRow>,
        rates: List<FarmModeRate>,
    ): List<FarmRun> {
        val ratesByFarmItem = rates.groupBy { it.projectId to it.itemId }
        val assigned = lines.mapNotNull { line ->
            val producer = producers[line.itemId] ?: return@mapNotNull null
            val mode = ratesByFarmItem[producer.projectId to line.itemId]
                ?.sortedWith(compareByDescending<FarmModeRate> { it.ratePerHour }.thenBy { it.modePosition })
                ?.first()
                ?: FarmModeRate(producer.projectId, null, null, 0, line.itemId, producer.ratePerHour)
            Triple(producer, mode, line)
        }
        return assigned
            .groupBy { (producer, _, _) -> producer.projectId }
            .map { (projectId, entries) ->
                val modes = entries
                    .groupBy { (_, mode, _) -> mode.modeId }
                    .map { (modeId, inMode) ->
                        val mode = inMode.first().second
                        ModeRun(
                            modeId = modeId,
                            modeName = mode.modeName,
                            modePosition = mode.modePosition,
                            items = inMode
                                .map { (_, rate, line) -> ItemRun(line, rate.ratePerHour) }
                                .sortedByDescending { it.line.quantity },
                        )
                    }
                    .sortedBy { it.modePosition }
                FarmRun(projectId = projectId, projectName = entries.first().first.projectName, modes = modes)
            }
            .sortedBy { it.projectName }
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
