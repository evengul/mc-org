package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.handler.respondRefusal
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.templated.settings.versionImpactFragment
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldVersion() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    handlePipeline(
        onSuccess = { version ->
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-version-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "World Version Updated",
                    message = "This world now plans against Minecraft $version.",
                )
            })
        },
    ) {
        val version = ValidateWorldVersionInputStep.run(parameters)
        UpdateWorldVersionStep(worldId).run(version)
        version
    }
}

/**
 * MCO-157: the preflight behind the Game Version selector.
 *
 * A GET because it changes nothing — it answers "what would switching to this version cost me?"
 * and the answer is read straight back into the settings page under the selector. The confirm
 * button it renders is what actually PATCHes.
 */
suspend fun ApplicationCall.handleGetWorldVersionImpact() {
    val worldId = this.getWorldId()
    val requested = request.queryParameters["version"]?.trim()

    val supported = GetSupportedVersionsStep.getSupportedVersions()
    val target = supported.firstOrNull { it.toString() == requested }
    if (target == null) {
        respondRefusal(
            HttpStatusCode.UnprocessableEntity,
            "Not available",
            "Seam has no item data for that version, so a world cannot switch to it.",
            alertId = "version-error",
        )
        return
    }

    val current = GetWorldVersionStep.process(worldId).getOrNull()
    if (current == target.toString()) {
        respondHtml(versionImpactFragment(worldId, null))
        return
    }

    val impact = when (val result = worldVersionImpact(worldId, target.toString())) {
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
        is Result.Success -> result.value
    }

    respondHtml(versionImpactFragment(worldId, impact))
}

/**
 * Accepts only a version this instance has actually ingested.
 *
 * Parsing used to be the whole check, which let any well-formed string through — including one
 * with no rows in `minecraft_items`. A world pointed at an uningested version has an empty
 * catalog: every plan blocks, every item picker is empty, and nothing says why. The allowed set
 * is the same one the selector renders, so the UI and the endpoint cannot disagree.
 */
object ValidateWorldVersionInputStep : Step<Parameters, AppFailure.ValidationError, MinecraftVersion> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, MinecraftVersion> {
        val versionString = input["version"]?.trim()
        if (versionString.isNullOrBlank()) {
            return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("version")))
            )
        }

        val supported = GetSupportedVersionsStep.getSupportedVersions()
        val match = supported.firstOrNull { it.toString() == versionString }
            ?: return Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.InvalidValue("version", supported.map { it.toString() }))
                )
            )

        return Result.success(match)
    }
}

/** The world's current version string, for the preflight's "you are already here" shortcut. */
object GetWorldVersionStep : Step<Int, AppFailure.DatabaseError, String?> {
    private val query = DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
        parameterSetter = { statement, worldId -> statement.setInt(1, worldId) },
        resultMapper = { if (it.next()) it.getString("version") else null },
    )

    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, String?> = query.process(input)
}

/**
 * Moves the world to [MinecraftVersion] and, when that is a change, drops every stored plan in it
 * (MCO-578): a different version can change every recipe and loot table a plan was derived from.
 *
 * One statement, so the two cannot come apart. If the drop could fail on its own, the world would
 * be on the new version with every plan still marked current for the old one — and the roadmap
 * never compares the fingerprint that would notice. Only `project_demand_state` changes; the rows
 * stay readable until the next roadmap load re-derives them. The `IS DISTINCT FROM` guard makes
 * re-saving the current version a no-op rather than a world-wide re-derivation.
 *
 * Every project in the world with a state row is invalidated, and so is every one that can be in
 * the roadmap's fill loop without one — gathering or demand rows, coverage's own candidates: it
 * may be on its first derivation with the old version already read, and the generation bump is
 * what stops it storing that plan as current (MCO-584). A project with neither gets no row. Rows
 * are locked in project order.
 */
data class UpdateWorldVersionStep(val worldId: Int) : Step<MinecraftVersion, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: MinecraftVersion): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.query<MinecraftVersion, Unit>(
            SafeSQL.with("""
                WITH changed AS (
                    UPDATE world
                    SET version = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND version IS DISTINCT FROM ?
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
            parameterSetter = { statement, version ->
                statement.setString(1, version.toString())
                statement.setInt(2, worldId)
                statement.setString(3, version.toString())
            },
            resultMapper = { },
        ).process(input).map { worldId }
    }
}
