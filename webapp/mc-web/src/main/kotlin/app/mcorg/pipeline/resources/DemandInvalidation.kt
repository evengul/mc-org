package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

/**
 * Invalidation of materialised demand when a world's **supply** changes (MCO-404).
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
 * Of the three approaches on MCO-404, this is the first: delete the fingerprint at the moment
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
 * measured cost of *this* approach is one DELETE on an action nobody takes often, and the
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
 * The other input classes — a project's own targets, its collected counts, its plan overrides —
 * are **not** covered by this. They change on the project page, which re-derives and rewrites on
 * the spot; their staleness window is the roadmap between such a change and the next visit to
 * that project, and invalidating on every progress tick would re-derive so often that the
 * materialised table would stop paying for itself. That trade wants its own measurement.
 */
private val logger = LoggerFactory.getLogger("app.mcorg.pipeline.resources.DemandInvalidation")

/**
 * Drops the stored demand fingerprint of every project in [worldId] whose plan touches an item
 * that [producerProjectId] produces, so the next roadmap load re-derives them.
 *
 * The producer itself is excluded: a farm's own plan never sees its own output as supply
 * (`WorldFarmSuppliesInput.excludeProjectId`), so its demand cannot have changed.
 *
 * Only `project_demand_state` is deleted, never `project_demand`. The rows stay readable until
 * the re-derivation replaces them, so a roadmap load that races an invalidation shows the old
 * numbers rather than an empty graph — the same "one load behind" the fill-on-read path has
 * always had, and strictly better than a project blinking out of the table.
 *
 * Call it **before** the change when the change removes what it reads (deleting a production
 * row, deleting the project), and after when it does not (a state transition).
 *
 * @return the number of projects invalidated.
 */
data class InvalidateDemandSuppliedByStep(
    val worldId: Int,
    val producerProjectId: Int,
) : Step<Unit, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete(
                """
                DELETE FROM project_demand_state s
                WHERE s.project_id IN (
                    SELECT DISTINCT d.project_id
                    FROM project_demand d
                    JOIN projects c ON c.id = d.project_id
                    WHERE c.world_id = ?
                      AND d.project_id <> ?
                      AND d.item_id IN (
                          SELECT pp.item_id FROM project_productions pp WHERE pp.project_id = ?
                      )
                )
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, producerProjectId)
                statement.setInt(3, producerProjectId)
            },
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
