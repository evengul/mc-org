package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

/**
 * Invalidation of materialised demand when a world's **supply** changes (MCO-404), and the map of
 * every other input and what keeps it current (MCO-578, at the end).
 *
 * [DemandFingerprint] hashes every input a derivation reads, including the world's farm supply —
 * but it is only ever *checked* where demand is written (`GenerateGatheringPlanStep`, on the
 * project page). The roadmap reads the stored rows and derives only for projects that have none
 * at all ([GetWorldDemandCoverageStep]), so a project with **stale** rows served them forever.
 *
 * That gap is not hypothetical, because supply is world-scoped: marking one farm DONE changes
 * what every other project in the world has to gather. Their roadmap numbers stayed pre-farm
 * until somebody happened to open each project page.
 *
 * ## Why invalidate rather than revalidate on read
 *
 * Of the three approaches on MCO-404, this is the first: drop the fingerprint at the moment
 * supply changes and let the roadmap's existing fill-on-read path re-derive. It wins on
 * measurement, not on taste — against the real ingested `Forever world` (29 projects, 2 with
 * gathering rows, one of them the 555-target YAMS storage system):
 *
 * | Path                                            | Measured        |
 * | ----------------------------------------------- | --------------- |
 * | Warm roadmap load (all fingerprints present)     | 0.15 – 0.21 s   |
 * | Cold roadmap load (2 projects to derive)         | 1.50 s          |
 * | One 555-target derivation, cold                  | 0.63 – 0.83 s   |
 *
 * Revalidating on read (option 3) means one derivation per project per roadmap load — 0.7 s each
 * on this data, so a world where 29 projects have plans would spend ~20 s rendering a table. The
 * measured cost of *this* approach is one state-row write on an action nobody takes often, and the
 * re-derivation is paid lazily, once, by the next roadmap load — which is the cost fill-on-read
 * already permits and which the numbers above price at 0.7 s per affected project.
 *
 * ## Targeted by item, not by world
 *
 * Invalidating the whole world would be simpler and much more expensive: every planned project
 * would re-derive because one farm changed. A project's plan can only have changed if it touches
 * an item the producer produces, and `project_demand` already records exactly which items each
 * project's plan touched — so the invalidation is a join, and flipping the iron farm invalidates
 * the projects that gather iron and nobody else.
 *
 * ## What is deliberately *not* invalidated here
 *
 * Only the supplied **item set** matters. `DemandFingerprint` also hashes the producer's display
 * name (via `SupplySource.Farm`'s label), so renaming a farm changes the fingerprint — but the
 * derived demand rows are identical, so serving them is not stale in any way a reader could
 * observe. Rates are not in the fingerprint at all: V1 supply is unbounded (MCO-287), so a rate
 * is information rather than a constraint on the plan.
 *
 * ## Every input, and what keeps it current (MCO-578)
 *
 * Supply was the first input wired up, not the only one. Each input [DemandFingerprint] or
 * [GatheringPlanInput] reads, and the mechanism that makes a change to it reach the roadmap:
 *
 * | Input                                        | Kept current by                                   |
 * | -------------------------------------------- | ------------------------------------------------- |
 * | World farm supply                            | [InvalidateDemandSuppliedByStep], above           |
 * | World version                                | `UpdateWorldVersionStep`, in the same statement    |
 * | Preferred wood species (two doors)           | `UpdatePreferredWoodSpeciesStep`, in the same statement |
 * | A project's targets, collected counts, plan overrides and links | Trigger `invalidate_project_demand` (V2_73_0) |
 * | Re-ingested game data for the same version   | Ingestion epoch read, hashed, stored and compared ([GetWorldDemandCoverageStep]) |
 * | The planner, cost model, or this derivation  | [DemandFingerprint.REVISION], stored and compared |
 *
 * The project's own inputs were left alone by MCO-404 because the project page re-derives on the
 * spot. Sixteen files write them, though, and the mod's sync, Field Log edits, adopting
 * measurements and adding from a schematic never re-derive; neither does the FK's
 * `ON DELETE SET NULL` that unlinks a requirement when the project solving it is deleted. The
 * worry that invalidating on every progress tick would re-derive too often does not hold for an
 * invalidation that is only a state-row write: re-derivation is lazy, so it costs one derivation per
 * project that changed since the world's roadmap was last opened, however many ticks there were.
 * Hence a trigger rather than handler calls — the rule is about the tables, not the doors.
 *
 * ### What "invalidate" writes (MCO-584)
 *
 * Every path above does the same thing to `project_demand_state`: null the fingerprint and bump
 * the generation, inserting the row when there is none. It used to delete the row, and a
 * derivation already running — inputs read, ~0.7 s of planning left — then wrote it back with the
 * old inputs' fingerprint, and the roadmap served that plan as current. The generation is what
 * `SaveProjectDemandStep` compares against the one the derivation read before its inputs.
 *
 * The world-wide paths reach every project in the world, and the supply path every project that has
 * nothing current stored, rows or not: a project on its first derivation has no row to bump
 * otherwise, and its in-flight save would land unopposed.
 *
 * ### Accepted staleness
 *
 * - **Renaming a farm or a linked project.** Both names are in the fingerprint (the supply label),
 *   so the project page re-derives, but the demand rows come out identical, so nothing here
 *   invalidates. A linked project's state does not matter at all: a link supplies its item
 *   whatever state the other project is in (`ProjectSupply.fold`).
 * - **World settings that are not plan inputs.** Name, description, members, and the farm-scale
 *   threshold, which only decides how the roadmap *draws* a cycle (MCO-460), not what a plan holds.
 * - **Readers that are not the roadmap.** The project page's pending-farm notice
 *   (`PendingFarmSupply`, through `GetFarmSupplyEdgesStep`) and `ReporterStore` (the mod's storage
 *   view, polled every ~10 s per player) read other projects' `project_demand` without filling it
 *   in. After an invalidation they see the previous derivation until the roadmap or that
 *   project's own page re-derives it; the rows are still there, because every invalidation
 *   touches only the state. Filling in on those reads would put ~0.7 s per stale project on a
 *   page render and on the mod's poll.
 */
private val logger = LoggerFactory.getLogger("app.mcorg.pipeline.resources.DemandInvalidation")

/**
 * Invalidates the stored demand of every project in [worldId] whose plan touches an item that
 * [producerProjectId] produces, so the next roadmap load re-derives them.
 *
 * Projects with gathering rows and nothing current stored are invalidated too, whatever their
 * plan touches (MCO-584). They have no `project_demand` rows to match on, so the item join cannot
 * see them, yet one may be on its first derivation right now — in the roadmap's fill loop — with
 * the pre-change supply already read. They are uncovered either way, so this costs nothing; what
 * it buys is the generation bump that stops that derivation storing its result as current.
 *
 * The producer itself is excluded: a farm's own plan never sees its own output as supply
 * (`WorldFarmSuppliesInput.excludeProjectId`), so its demand cannot have changed.
 *
 * Invalidating nulls the fingerprint and bumps the generation in `project_demand_state`
 * ([SaveProjectDemandStep] says why); `project_demand` is never touched. The rows stay readable
 * until the re-derivation replaces them, so a roadmap load that races an invalidation shows the old
 * numbers rather than an empty graph — the same "one load behind" the fill-on-read path has
 * always had, and strictly better than a project blinking out of the table.
 *
 * Call it **after** the change when the change leaves what it reads in place (a state transition).
 * When the change removes it (deleting a production row, deleting the project), call it before
 * the change **in the same transaction**, via [transactionConnection]. Committed on its own first,
 * the bump would come before the change, and a derivation starting in between would read the new
 * generation with the old supply and store its plan as current (MCO-584).
 *
 * It reads every runtime mode's items, not only the active mode's (MCO-413). A mode switch removes
 * one mode's items from supply and adds another's, and reading the union means one call after the
 * switch covers both, at the price of also invalidating consumers of a third mode nobody touched.
 *
 * @return the number of projects invalidated.
 */
data class InvalidateDemandSuppliedByStep(
    val worldId: Int,
    val producerProjectId: Int,
    val transactionConnection: TransactionConnection? = null,
) : Step<Unit, AppFailure.DatabaseError, Int> {

    // "Nothing current stored" is GetWorldDemandCoverageStep's test of a current row — a
    // fingerprint, this code's revision, and the version's latest ingestion epoch — so a project
    // the roadmap is about to re-derive after a deploy or a re-ingest is reached as well. Rows are
    // locked in project order, so two invalidations in one world cannot deadlock on each other.
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.with(
                """
                WITH invalidated AS (
                    INSERT INTO project_demand_state (project_id, fingerprint, derived_at, generation)
                    SELECT c.id, NULL, NULL, 1
                    FROM projects c
                    WHERE c.world_id = ?
                      AND c.id <> ?
                      AND (
                          EXISTS (SELECT 1 FROM project_demand d
                                  WHERE d.project_id = c.id
                                    AND d.item_id IN (
                                        SELECT pp.item_id FROM project_productions pp WHERE pp.project_id = ?
                                    ))
                          OR (EXISTS (SELECT 1 FROM resource_gathering rg WHERE rg.project_id = c.id)
                              AND NOT EXISTS (
                                  SELECT 1 FROM project_demand_state s
                                  WHERE s.project_id = c.id
                                    AND s.fingerprint IS NOT NULL
                                    AND s.revision = ?
                                    AND NOT EXISTS (
                                        SELECT 1 FROM world w
                                        JOIN minecraft_version_ingestion i
                                          ON i.version = w.version AND i.status = 'completed'
                                        WHERE w.id = c.world_id
                                          AND (s.game_data_epoch IS NULL OR s.game_data_epoch < i.completed_at)
                                    )))
                      )
                    ORDER BY c.id
                    ON CONFLICT (project_id) DO UPDATE
                        SET fingerprint = NULL, generation = project_demand_state.generation + 1
                    RETURNING project_id
                )
                SELECT count(*) AS invalidated FROM invalidated
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, producerProjectId)
                statement.setInt(3, producerProjectId)
                statement.setInt(4, DemandFingerprint.REVISION)
            },
            resultMapper = { rs -> rs.next(); rs.getInt("invalidated") },
            transactionConnection = transactionConnection,
        ).process(Unit)
}

/**
 * Fire-and-forget wrapper for the handlers.
 *
 * Invalidation runs after the mutation it follows has already committed, on a request whose
 * result is a rendered fragment. Failing that request because a cache invalidation failed would
 * turn a stale number into a visible error, so a failure is logged and swallowed — the same
 * posture as the write-through in `GenerateGatheringPlanStep`. The cost of the swallowed case is
 * one world's roadmap staying one derivation behind until the next supply change or project
 * visit.
 */
suspend fun invalidateDemandSuppliedBy(worldId: Int, producerProjectId: Int) {
    when (val result = InvalidateDemandSuppliedByStep(worldId, producerProjectId).process(Unit)) {
        is Result.Success ->
            if (result.value > 0) {
                logger.debug(
                    "Demand: invalidated {} project(s) in world {} after a supply change in project {}",
                    result.value, worldId, producerProjectId,
                )
            }
        // No exception and no row data in the message: a PostgreSQL error appends
        // `DETAIL: Key (col)=(value)`, which is user content. See documentation/logging.md.
        is Result.Failure ->
            logger.warn(
                "Demand: could not invalidate stored demand in world {} after a supply change in project {}",
                worldId, producerProjectId,
            )
    }
}

/**
 * The state half of the rule, shared by the two doors that move a project's state — the Field Log
 * badge and the project page's inline editor.
 *
 * DONE is the producing condition (MCO-287): crossing it in either direction is what adds or
 * removes world supply. Every other transition leaves the supplied item set alone, so it costs
 * nothing here.
 */
suspend fun invalidateDemandOnStateChange(
    worldId: Int,
    projectId: Int,
    from: ProjectState,
    to: ProjectState,
) {
    if (from == ProjectState.DONE || to == ProjectState.DONE) {
        invalidateDemandSuppliedBy(worldId, projectId)
    }
}
