package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.pipeline.Step
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

data class UpdatePreferredWoodSpeciesStep(val worldId: Int) :
    Step<String?, AppFailure.DatabaseError, String?> {
    override suspend fun process(input: String?): Result<AppFailure.DatabaseError, String?> {
        return DatabaseSteps.update<String?>(
            sql = SafeSQL.update("""
                UPDATE world
                SET preferred_wood_species = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """),
            parameterSetter = { statement, species ->
                // setString(null) is fine on Postgres, but setNull is explicit about intent:
                // clearing the preference is a supported answer, not a missing value.
                if (species == null) statement.setNull(1, Types.VARCHAR)
                else statement.setString(1, species)
                statement.setInt(2, worldId)
            }
        ).process(input).map { input }
    }
}
