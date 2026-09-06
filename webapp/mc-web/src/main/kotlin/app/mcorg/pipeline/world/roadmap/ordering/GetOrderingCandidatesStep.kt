package app.mcorg.pipeline.world.roadmap.ordering

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.OrderingCandidate
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/** Which of the two pickers is being searched — and therefore which way a cycle would close. */
enum class OrderingField {
    /** `DO THIS FIRST` — the prerequisite. The counterpart, if picked, is the dependent. */
    FIRST,

    /** `BEFORE THIS` — the dependent. The counterpart, if picked, is the prerequisite. */
    THEN,
}

/**
 * Every project in the world, each marked with whether picking it would close a loop
 * (MCO-302).
 *
 * **The whole world, not a filtered candidate list.** `GetAvailableProjectDependenciesStep`
 * makes cycles unofferable by *excluding* them, which is right for a closed dropdown and
 * wrong for a search box: a search that matches on name has to explain why the row it found
 * is not pickable, or the user retypes the same query wondering where the project went.
 * Marking also beats refusing on submit, which discards a reason the user has already
 * written and still does not say which end to reconsider.
 *
 * A world holds a few dozen projects, so the query returns all of them and the query string
 * filters in Kotlin. That keeps `LIKE` escaping out of the SQL and lets the results panel
 * report "2 of 29 projects match" without a second count.
 *
 * Cycles are computed over `project_dependencies` alone. Loops through *derived* supply edges
 * are a different thing with a different answer — they are real, both edges are true, and
 * MCO-460's `roadmap_cycle_order` asks the user which comes first rather than forbidding
 * them. What must never happen is a hand-typed loop, because nothing derived it and nobody
 * meant it.
 *
 * [counterpartId] is the project already chosen in the *other* picker, or null while it is
 * empty — nothing can cycle against nothing, so an unset counterpart marks nothing.
 */
data class GetOrderingCandidatesStep(
    val worldId: Int,
    val field: OrderingField,
    val counterpartId: Int?,
) : Step<Unit, AppFailure.DatabaseError, List<OrderingCandidate>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<OrderingCandidate>> {
        // -1 matches no project, so an unset counterpart yields an empty chain and no marks.
        val counterpart = counterpartId ?: -1

        return DatabaseSteps.query<Unit, List<OrderingCandidate>>(
            sql = when (field) {
                OrderingField.FIRST -> DEPENDENTS_OF_COUNTERPART
                OrderingField.THEN -> PREREQUISITES_OF_COUNTERPART
            },
            // Placeholders are numbered by position in the statement text, and the marking
            // expression sits in the SELECT list — so the world filter is third, not second.
            parameterSetter = { statement, _ ->
                statement.setInt(1, counterpart) // chain root
                statement.setInt(2, counterpart) // a project is always its own cycle
                statement.setInt(3, worldId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            OrderingCandidate(
                                projectId = resultSet.getInt("id"),
                                name = resultSet.getString("name"),
                                state = ProjectState.valueOf(resultSet.getString("state")),
                                wouldCycle = resultSet.getBoolean("would_cycle"),
                            )
                        )
                    }
                }
            }
        ).process(input)
    }

    private companion object {
        /**
         * Searching `DO THIS FIRST`, so the pick would become the prerequisite of the
         * counterpart. That closes a loop exactly when the pick already waits on the
         * counterpart — directly or through any chain.
         */
        val DEPENDENTS_OF_COUNTERPART = SafeSQL.with("""
            WITH RECURSIVE waiting_on_counterpart AS (
                SELECT project_id
                FROM project_dependencies
                WHERE depends_on_project_id = ?

                UNION

                SELECT pd.project_id
                FROM project_dependencies pd
                JOIN waiting_on_counterpart w ON pd.depends_on_project_id = w.project_id
            )
            SELECT
              p.id    AS id,
              p.name  AS name,
              p.state AS state,
              (p.id IN (SELECT project_id FROM waiting_on_counterpart) OR p.id = ?) AS would_cycle
            FROM projects p
            WHERE p.world_id = ?
            ORDER BY p.name
        """.trimIndent())

        /**
         * Searching `BEFORE THIS`, so the pick would become the dependent of the counterpart.
         * That closes a loop exactly when the counterpart already waits on the pick.
         */
        val PREREQUISITES_OF_COUNTERPART = SafeSQL.with("""
            WITH RECURSIVE counterpart_waits_on AS (
                SELECT depends_on_project_id AS project_id
                FROM project_dependencies
                WHERE project_id = ?

                UNION

                SELECT pd.depends_on_project_id
                FROM project_dependencies pd
                JOIN counterpart_waits_on c ON pd.project_id = c.project_id
            )
            SELECT
              p.id    AS id,
              p.name  AS name,
              p.state AS state,
              (p.id IN (SELECT project_id FROM counterpart_waits_on) OR p.id = ?) AS would_cycle
            FROM projects p
            WHERE p.world_id = ?
            ORDER BY p.name
        """.trimIndent())
    }
}
