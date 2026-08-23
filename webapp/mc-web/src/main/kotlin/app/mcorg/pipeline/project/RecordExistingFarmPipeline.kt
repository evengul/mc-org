package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.event.ProjectCreated
import app.mcorg.event.actorDisplayName
import app.mcorg.event.eventBus
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import java.time.Instant

/** One produced item staged in the record-a-farm form. */
data class FarmProductionInput(
    val itemId: String,
    val name: String,
    val ratePerHour: Int,
)

data class RecordExistingFarmInput(
    val name: String,
    val description: String,
    val type: ProjectType,
    val location: MinecraftLocation?,
    val productions: List<FarmProductionInput>,
)

/**
 * MCO-298 — "record an existing farm": a farm that predates Seam enters the world
 * already producing, so it is created directly in the operational state
 * (`stage COMPLETED` + `state DONE`) with its productions attached.
 *
 * This deliberately bypasses [app.mcorg.domain.model.project.ProjectState.allowedTransitions]
 * (there is no `PENDING -> DONE` edge): the state machine describes a project Seam
 * planned and watched get built. A pre-existing farm was never planned here — there is
 * no transition to make, only a fact to record. Under the MCO-287 anchor decision
 * `DONE` *is* the producing condition, so recording it DONE is what makes it supply
 * other projects' plans ([app.mcorg.pipeline.resources.GetWorldFarmSuppliesStep]).
 */
suspend fun ApplicationCall.handleRecordExistingFarm() {
    val parameters = receiveParameters()
    val user = getUser()
    val worldId = getWorldId()
    val bus = eventBus
    val isHtmx = request.headers["HX-Request"] == "true"
    val validItems = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { _: Int ->
            // The Field Log groups by state, and a recorded farm lands in a different
            // group than the one the user is looking at — reload rather than splice a
            // card into the wrong section.
            if (isHtmx) {
                response.headers.append("HX-Redirect", "/worlds/$worldId/projects")
                respondHtml("")
            } else {
                response.headers.append("Location", "/worlds/$worldId/projects")
                respond(HttpStatusCode.SeeOther, "")
            }
        }
    ) {
        val input = ValidateRecordExistingFarmInputStep(validItems).run(parameters)
        ValidateWorldMemberRole<RecordExistingFarmInput>(user, Role.ADMIN, worldId).run(input)
        val projectId = CreateExistingFarmStep(worldId).run(input)
        // A farm recorded straight into DONE is new world supply the moment it exists, so every
        // stored plan that gathers what it makes is now wrong (MCO-404).
        invalidateDemandSuppliedBy(worldId, projectId)
        CacheManager.onProjectCreated(worldId, projectId)
        bus.publish(
            ProjectCreated(
                worldId,
                user.id,
                Instant.now(),
                projectId,
                input.name,
                input.type,
                actorName = user.actorDisplayName()
            )
        )
        projectId
    }
}

/**
 * Validates the record-a-farm form.
 *
 * Field names are farm-prefixed because the create-project modal lives on the same page
 * and the validation errors are swapped out-of-band by `validation-error-<parameter>` id —
 * a shared `name` would land in the wrong (hidden) dialog.
 *
 * Produced items arrive as `productions[<itemId>]=<rate>` hidden inputs staged by
 * farm-modal.js. At least one is required: a farm recorded with no output is a Done
 * project that supplies nothing and is easy to never notice again.
 */
data class ValidateRecordExistingFarmInputStep(val validItems: List<Item>) :
    Step<Parameters, AppFailure.ValidationError, RecordExistingFarmInput> {

    private val productionParameter = Regex("""^productions\[(.+)]$""")

    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, RecordExistingFarmInput> {
        val errors = mutableListOf<ValidationFailure>()

        val name = input["farmName"]?.trim().orEmpty()
        if (name.isBlank()) {
            errors.add(ValidationFailure.MissingParameter("farmName"))
        } else if (name.length < 3 || name.length > 100) {
            errors.add(ValidationFailure.CustomValidation("farmName", "Name must be between 3 and 100 characters"))
        }

        val description = input["farmDescription"]?.trim().orEmpty()
        if (description.length > 500) {
            errors.add(
                ValidationFailure.CustomValidation("farmDescription", "Description must be at most 500 characters")
            )
        }

        val typeRaw = input["farmType"]?.takeIf { it.isNotBlank() }
        val type = if (typeRaw == null) {
            ProjectType.FARMING
        } else {
            runCatching { ProjectType.valueOf(typeRaw.uppercase()) }.getOrElse {
                errors.add(ValidationFailure.InvalidValue("farmType", ProjectType.entries.map { entry -> entry.name }))
                ProjectType.FARMING
            }
        }

        val location = parseLocation(input, errors)
        val productions = parseProductions(input, errors)

        return if (errors.isEmpty()) {
            Result.success(
                RecordExistingFarmInput(
                    name = name,
                    description = description,
                    type = type,
                    location = location,
                    productions = productions,
                )
            )
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }

    /**
     * Location is optional as a whole, but partial coordinates are a mistake, not a
     * shorthand — X and Z go together (Y defaults to 0 and the dimension to the
     * Overworld, matching the inline location editor).
     */
    private fun parseLocation(input: Parameters, errors: MutableList<ValidationFailure>): MinecraftLocation? {
        val x = input["farmX"]?.takeIf { it.isNotBlank() }
        val y = input["farmY"]?.takeIf { it.isNotBlank() }
        val z = input["farmZ"]?.takeIf { it.isNotBlank() }
        if (x == null && y == null && z == null) return null

        if (x == null || z == null) {
            errors.add(ValidationFailure.CustomValidation("farmLocation", "Give both X and Z, or leave the location empty"))
            return null
        }

        val parsedX = x.toIntOrNull()
        val parsedY = y?.toIntOrNull() ?: 0
        val parsedZ = z.toIntOrNull()
        if (parsedX == null || parsedZ == null || (y != null && y.toIntOrNull() == null)) {
            errors.add(ValidationFailure.CustomValidation("farmLocation", "Coordinates must be whole numbers"))
            return null
        }

        val dimensionRaw = input["farmDimension"]?.takeIf { it.isNotBlank() }
        val dimension = if (dimensionRaw == null) {
            Dimension.OVERWORLD
        } else {
            runCatching { Dimension.valueOf(dimensionRaw.uppercase()) }.getOrElse {
                errors.add(ValidationFailure.InvalidValue("farmLocation", Dimension.entries.map { entry -> entry.name }))
                return null
            }
        }

        return MinecraftLocation(dimension = dimension, x = parsedX, y = parsedY, z = parsedZ)
    }

    private fun parseProductions(
        input: Parameters,
        errors: MutableList<ValidationFailure>,
    ): List<FarmProductionInput> {
        val itemsById = validItems.associateBy { it.id }
        val staged = input.entries()
            .mapNotNull { entry -> productionParameter.find(entry.key)?.groupValues?.get(1)?.to(entry.value.firstOrNull()) }
            // The same item staged twice would collide on the (project_id, item_id)
            // unique index (V2_50_0); the last rate entered wins, as in the panel's upsert.
            .associate { (itemId, rate) -> itemId to rate }

        if (staged.isEmpty()) {
            errors.add(ValidationFailure.CustomValidation("productions", "Add at least one produced item"))
            return emptyList()
        }

        return staged.mapNotNull { (itemId, rateRaw) ->
            val item = itemsById[itemId]
            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("productions", "Unknown item: $itemId"))
                return@mapNotNull null
            }
            val rate = if (rateRaw.isNullOrBlank()) 0 else rateRaw.toIntOrNull()
            if (rate == null || rate < 0) {
                errors.add(
                    ValidationFailure.CustomValidation("productions", "Rate for ${item.name} must be a non-negative whole number")
                )
                return@mapNotNull null
            }
            FarmProductionInput(itemId = item.id, name = item.name, ratePerHour = rate)
        }
    }
}

/**
 * Inserts the farm project and its productions in one transaction — a farm without its
 * produced items would be an ordinary Done project, silently supplying nothing.
 */
data class CreateExistingFarmStep(val worldId: Int) :
    Step<RecordExistingFarmInput, AppFailure.DatabaseError, Int> {

    override suspend fun process(input: RecordExistingFarmInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction<RecordExistingFarmInput, Int> { connection ->
            object : Step<RecordExistingFarmInput, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: RecordExistingFarmInput): Result<AppFailure.DatabaseError, Int> {
                    val projectIdResult = DatabaseSteps.update<RecordExistingFarmInput>(
                        sql = SafeSQL.insert("""
                            INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension)
                            VALUES (?, ?, ?, ?, 'COMPLETED', 'DONE', ?, ?, ?, ?)
                            RETURNING id
                        """.trimIndent()),
                        parameterSetter = { statement, farm ->
                            statement.setInt(1, worldId)
                            statement.setString(2, farm.name)
                            statement.setString(3, farm.description)
                            statement.setString(4, farm.type.name)
                            val location = farm.location
                            if (location == null) {
                                statement.setNull(5, java.sql.Types.INTEGER)
                                statement.setNull(6, java.sql.Types.INTEGER)
                                statement.setNull(7, java.sql.Types.INTEGER)
                                statement.setNull(8, java.sql.Types.VARCHAR)
                            } else {
                                statement.setInt(5, location.x)
                                statement.setInt(6, location.y)
                                statement.setInt(7, location.z)
                                statement.setString(8, location.dimension.name)
                            }
                        },
                        transactionConnection = connection
                    ).process(input)

                    if (projectIdResult is Result.Failure) {
                        return Result.Failure(projectIdResult.error)
                    }
                    val projectId = projectIdResult.getOrNull()!!

                    val productions = DatabaseSteps.batchUpdate<FarmProductionInput>(
                        SafeSQL.insert("""
                            INSERT INTO project_productions (project_id, item_id, name, rate_per_hour)
                            VALUES (?, ?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, production ->
                            statement.setInt(1, projectId)
                            statement.setString(2, production.itemId)
                            statement.setString(3, production.name)
                            statement.setInt(4, production.ratePerHour)
                        },
                        transactionConnection = connection
                    ).process(input.productions)

                    if (productions is Result.Failure) {
                        return Result.Failure(productions.error)
                    }

                    return Result.success(projectId)
                }
            }
        }.process(input)
    }
}
