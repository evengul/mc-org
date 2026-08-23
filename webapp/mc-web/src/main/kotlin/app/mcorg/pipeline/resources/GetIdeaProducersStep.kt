package app.mcorg.pipeline.resources

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * What to look up: the items a plan demands, and who is asking.
 *
 * The viewer is not optional. Visibility is the difference between a bank of one public design
 * and a bank of eleven (measured 2026-08-23: every idea carrying production data is `PRIVATE`,
 * and the only `PUBLIC` one declares no production), so a lookup that forgot the viewer would
 * quietly return nothing and read as "no design matches".
 */
data class IdeaProducerInput(
    val itemIds: Collection<String>,
    val viewerId: Int,
)

/**
 * The designs in the bank that produce any of [IdeaProducerInput.itemIds] (MCO-294).
 *
 * Uses `idea_production_rates_item_id_idx`, which V2_57_0 added with the comment "MCO-294's
 * lookup direction: which ideas produce this item, and how fast at best?" — this is that query.
 *
 * ## Visibility
 *
 * Public designs plus the viewer's own, which is
 * [app.mcorg.pipeline.idea.IdeaSqlBuilder]'s rule for the hub (MCO-291) and must stay the same
 * rule: a suggestion the viewer cannot then open would be worse than no suggestion. `is_active`
 * goes with it — an idea being edited is not in the hub and should not be suggested either.
 *
 * ## MAX over modes
 *
 * An idea can describe several ways of running the same farm (V2_57_0), so a rate is picked per
 * item as the best any mode achieves. That mixes modes in principle — the fastest mode for bones
 * need not be the fastest for blaze rods — and does not in practice: every idea in the bank has
 * exactly one mode. MCO-413 is where the project records which mode it is actually run in; until
 * then, "how fast can this design make this" is the only question that can honestly be answered.
 * `MAX` skips NULLs, so an item returns null only when no mode ever measured it.
 */
object GetIdeaProducersStep : Step<IdeaProducerInput, AppFailure, List<IdeaProducer>> {

    private val query = DatabaseSteps.query<IdeaProducerInput, List<Row>>(
        sql = SafeSQL.select(
            """
            SELECT m.idea_id, i.name AS idea_name, r.item_id, MAX(r.rate_per_hour) AS rate_per_hour
            FROM idea_production_rates r
            JOIN idea_production_modes m ON m.id = r.mode_id
            JOIN ideas i ON i.id = m.idea_id
            WHERE r.item_id = ANY(?)
              AND i.is_active = TRUE
              AND (i.visibility = ? OR i.created_by = ?)
            GROUP BY m.idea_id, i.name, r.item_id
            """.trimIndent()
        ),
        parameterSetter = { ps, input ->
            ps.setArray(1, ps.connection.createArrayOf("text", input.itemIds.toTypedArray()))
            ps.setString(2, IdeaVisibility.PUBLIC.name)
            ps.setInt(3, input.viewerId)
        },
        resultMapper = { rs ->
            buildList {
                while (rs.next()) {
                    val rate = rs.getInt("rate_per_hour").takeUnless { rs.wasNull() }
                    add(Row(rs.getInt("idea_id"), rs.getString("idea_name"), rs.getString("item_id"), rate))
                }
            }
        }
    )

    override suspend fun process(input: IdeaProducerInput): Result<AppFailure, List<IdeaProducer>> {
        // ANY('{}') matches nothing, but a plan with no demand is the common empty-project case
        // and does not deserve a round trip.
        if (input.itemIds.isEmpty()) return Result.success(emptyList())

        return when (val r = query.process(input)) {
            is Result.Failure -> r
            is Result.Success -> Result.success(
                r.value
                    .groupBy { it.ideaId to it.ideaName }
                    .map { (idea, rows) ->
                        IdeaProducer(
                            ideaId = idea.first,
                            ideaName = idea.second,
                            rates = rows.associate { it.itemId to it.ratePerHour },
                        )
                    }
            )
        }
    }

    private data class Row(val ideaId: Int, val ideaName: String, val itemId: String, val ratePerHour: Int?)
}
