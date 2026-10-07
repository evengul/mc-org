package app.mcorg.pipeline.world.settings.general

import app.mcorg.pipeline.Step
import app.mcorg.engine.plan.MemberPrior
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import java.sql.Types

/**
 * Updates which tree a world farms (MCO-409) — the one answer that settles `#planks`,
 * `#wooden_slabs` and `#logs` instead of asking three times per project.
 */
suspend fun ApplicationCall.handleUpdatePreferredWoodSpecies() {
    val parameters = receiveParameters()
    val worldId = getWorldId()

    handlePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "preferred-wood-species-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "Wood preference updated",
                    autoClose = true
                )
            })
        },
    ) {
        val species = ValidatePreferredWoodSpeciesStep.run(parameters)
        UpdatePreferredWoodSpeciesStep(worldId).run(species)
    }
}

/**
 * One of [MemberPrior.SPECIES], or empty for "ask me".
 *
 * Validated against the engine's own list rather than a copy, so the form, the planner and the
 * column's CHECK constraint cannot drift apart. Empty is a real answer — it clears the
 * preference and puts the wood tags back to being asked, which is what a player who has not
 * settled on a tree should get.
 */
object ValidatePreferredWoodSpeciesStep : Step<Parameters, AppFailure.ValidationError, String?> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String?> {
        val raw = input["preferredWoodSpecies"]?.trim().orEmpty()
        if (raw.isEmpty()) return Result.success(null)

        return if (MemberPrior.isKnownSpecies(raw)) {
            Result.success(raw)
        } else {
            Result.failure(
                AppFailure.ValidationError(
                    listOf(
                        ValidationFailure.CustomValidation(
                            "preferredWoodSpecies",
                            "That is not a wood this version knows about"
                        )
                    )
                )
            )
        }
    }
}

/**
 * Sets the world's tree and, when that is a change, drops every stored plan in it (MCO-578):
 * every wood tag in every plan resolves differently afterwards. One statement, for the reason
 * [UpdateWorldVersionStep] gives.
 *
 * The invalidation lives in the step rather than the handler because there are two doors to this
 * column — world settings, and the drill's "and use this wood everywhere" box (MCO-487) — and a
 * rule kept in one handler is a rule the other door skips.
 */
data class UpdatePreferredWoodSpeciesStep(val worldId: Int) :
    Step<String?, AppFailure.DatabaseError, String?> {
    override suspend fun process(input: String?): Result<AppFailure.DatabaseError, String?> {
        return DatabaseSteps.query<String?, Unit>(
            sql = SafeSQL.with("""
                WITH changed AS (
                    UPDATE world
                    SET preferred_wood_species = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND preferred_wood_species IS DISTINCT FROM ?
                    RETURNING id
                ), dropped AS (
                    INSERT INTO project_demand_state (project_id, fingerprint, derived_at, generation)
                    SELECT p.id, NULL, NULL, 1
                    FROM projects p
                    JOIN changed c ON c.id = p.world_id
                    WHERE EXISTS (SELECT 1 FROM resource_gathering rg WHERE rg.project_id = p.id)
                       OR EXISTS (SELECT 1 FROM project_demand d WHERE d.project_id = p.id)
                       OR EXISTS (SELECT 1 FROM project_demand_state s WHERE s.project_id = p.id)
                    ORDER BY p.id
                    ON CONFLICT (project_id) DO UPDATE
                        SET fingerprint = NULL, generation = project_demand_state.generation + 1
                    RETURNING project_id
                )
                SELECT (SELECT count(*) FROM dropped) AS dropped
            """),
            parameterSetter = { statement, species ->
                // setString(null) is fine on Postgres, but setNull is explicit about intent:
                // clearing the preference is a supported answer, not a missing value.
                if (species == null) {
                    statement.setNull(1, Types.VARCHAR)
                    statement.setNull(3, Types.VARCHAR)
                } else {
                    statement.setString(1, species)
                    statement.setString(3, species)
                }
                statement.setInt(2, worldId)
            },
            resultMapper = { },
        ).process(input).map { input }
    }
}
