package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * The world's declared wood species (MCO-409) — which tree it farms, or null when unanswered.
 *
 * One column rather than [app.mcorg.pipeline.world.commonsteps.GetWorldStep], for the same
 * reason [GetFarmScaleThresholdStep] exists: the planner needs this and nothing else about the
 * world, and GetWorldStep's aggregate joins every project to count them.
 *
 * Null is the honest answer for a missing world as well as an unanswered one — both mean "no
 * species to apply", and the effect is identical: the wood tags stay open and asked, which is
 * the behaviour that predates this preference.
 */
object GetPreferredWoodSpeciesStep : Step<Int, AppFailure, String?> {

    private val query = DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT preferred_wood_species FROM world WHERE id = ?"),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs -> if (rs.next()) rs.getString("preferred_wood_species") else null }
    )

    override suspend fun process(input: Int): Result<AppFailure, String?> = query.process(input)
}
