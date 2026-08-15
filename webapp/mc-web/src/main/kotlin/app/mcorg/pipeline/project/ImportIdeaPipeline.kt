package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.event.IdeaImported
import app.mcorg.event.eventBus
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.handler.handlePipeline
import io.ktor.server.response.respond
import io.ktor.http.Parameters
import io.ktor.http.HttpStatusCode
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templated.dsl.pages.importReviewPage
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.domain.model.user.Role
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.serialization.json.Json
import java.time.Instant

data class IdeaForImport(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val requirements: Map<Item, Int>,
    val production: Map<Item, Int>
)

/**
 * Step one of an idea import (MCO-306): show what the idea would create, before creating it.
 *
 * This matters more here than for a schematic. A schematic can be edited in Litematica and
 * re-uploaded; an idea is someone else's list, and this screen is the only place to say
 * "not that part" before it becomes a hundred resource rows.
 *
 * A GET, unlike the schematic flow's review — the material list comes from the database, so
 * the page is reloadable and shareable rather than tied to one upload.
 */
suspend fun ApplicationCall.handleReviewIdeaImport() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()

    val worldId = request.queryParameters["worldId"]?.toIntOrNull()
        ?: return defaultHandleError(
            AppFailure.customValidationError("worldId", "Pick a world to import into")
        )

    val taskId = request.queryParameters["forTask"]?.toIntOrNull()
    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { idea: IdeaForImport ->
            respondHtml(
                importReviewPage(
                    user = user,
                    worldId = worldId,
                    worldName = getWorldName(worldId),
                    projectName = idea.name,
                    requirements = idea.requirements,
                    warnings = computeImportWarnings(worldId, idea.requirements),
                    action = Link.Ideas.single(ideaId) + "/import",
                    hiddenFields = buildMap {
                        put("worldId", worldId.toString())
                        taskId?.let { put("forTask", it.toString()) }
                    },
                )
            )
        }
    ) {
        ValidateWorldMemberRole<Pair<Int, Int>>(user, Role.ADMIN, worldId).run(worldId to ideaId)
        ValidateVersionRangeStep.run(worldId to ideaId)
        val ideaData = GetIdeaForImportStep.run(ideaId)
        ValidateItemIdsStep(items).run(ideaData)
    }
}

/**
 * Step two: create the project from the **reviewed** requirements.
 *
 * Only the requirements are reviewed. An idea's productions describe what the farm puts out
 * (MCO-287's supply model) — they are not work you are agreeing to do, so they are not the
 * user's to exclude here and carry through untouched, along with the idea link, the starter
 * tasks and any `forTask` dependency.
 */
suspend fun ApplicationCall.handleImportIdea() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()
    val bus = this.eventBus

    val submitted = this.receiveParameters()
    val worldId = (submitted["worldId"] ?: parameters["worldId"])?.toIntOrNull()
        ?: return run {
            defaultHandleError(AppFailure.customValidationError("worldId", "Invalid or missing worldId parameter"))
        }

    val taskId = (submitted["forTask"] ?: parameters["forTask"])?.toIntOrNull()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { projectId: Int ->
            val target = Link.Worlds.world(worldId).project(projectId).to
            // The review page submits as a plain form, so a browser needs a real redirect;
            // an HX-Redirect header would be silently ignored and leave a blank page.
            if (request.headers["HX-Request"] == "true") {
                clientRedirect(target)
            } else {
                response.headers.append("Location", target)
                respond(HttpStatusCode.SeeOther, "")
            }
        }
    ) {
        ValidateWorldMemberRole<Pair<Int, Int>>(user, Role.ADMIN, worldId).run(worldId to ideaId)
        ValidateVersionRangeStep.run(worldId to ideaId)
        val ideaData = GetIdeaForImportStep.run(ideaId)
        val validatedIdea = ValidateItemIdsStep(items).run(ideaData)
        val reviewedIdea = ApplyReviewedRequirementsStep(submitted, items).run(validatedIdea)
        val projectId = CreateProjectFromIdeaStep(worldId, taskId).run(reviewedIdea)
        CacheManager.onProjectCreated(worldId, projectId)
        bus.publish(IdeaImported(worldId, user.id, Instant.now(), ideaId, reviewedIdea.name))
        projectId
    }
}

/**
 * Replaces an idea's requirements with the ones the review page sent back.
 *
 * The whole list arrives in one `materials` field (MCO-315); rows the user struck are carried
 * too and dropped here. Ids are checked against the world's catalog rather than against the
 * idea, so a submission cannot smuggle in an item the idea never listed.
 *
 * There is deliberately no "not reviewed, use the idea's list" fallback. A form with every
 * row unchecked and a bare direct post are near-identical, so a fallback would silently
 * import everything at exactly the moment the user asked for nothing. Import goes through
 * the review screen; a submission with no rows — or with no list at all — is refused.
 */
internal data class ApplyReviewedRequirementsStep(
    val submitted: Parameters,
    val availableItems: List<Item>,
) : Step<IdeaForImport, AppFailure, IdeaForImport> {

    override suspend fun process(input: IdeaForImport): Result<AppFailure, IdeaForImport> {
        val submittedRows = when (
            val decoded = ReviewedMaterialsCodec.decode(submitted[ReviewedMaterialsCodec.FIELD])
        ) {
            is Result.Failure -> return Result.Failure(decoded.error)
            is Result.Success -> decoded.value
        }

        val byId = availableItems.associateBy { it.id }
        val errors = mutableListOf<ValidationFailure>()
        val requirements = mutableMapOf<Item, Int>()

        submittedRows.forEach { row ->
            if (!row.included) return@forEach
            // The review screen never offers air, so this only catches a hand-rolled post —
            // but "air is never a material" is worth enforcing where the list becomes final.
            if (row.itemId in NON_MATERIAL_FILL) return@forEach

            val item = byId[row.itemId]
            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("materials", "Unknown item: ${row.itemId}"))
                return@forEach
            }
            requirements[item] = row.amount
        }

        if (errors.isNotEmpty()) return Result.failure(AppFailure.ValidationError(errors))
        if (requirements.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("materials", "Keep at least one material to import")
            )
        }

        val name = submitted["name"]?.trim()?.takeIf { it.isNotBlank() }?.take(100) ?: input.name
        return Result.success(input.copy(name = name, requirements = requirements))
    }
}

private object ValidateVersionRangeStep : Step<Pair<Int, Int>, AppFailure, Pair<Int, Int>> {
    override suspend fun process(input: Pair<Int, Int>): Result<AppFailure, Pair<Int, Int>> {
        val worldVersion = DatabaseSteps.query<Int, MinecraftVersion>(
            sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
            },
            resultMapper = { resultSet ->
                resultSet.next()
                MinecraftVersion.fromString(resultSet.getString("version"))
            }
        ).process(input.first)

        val ideaVersion = DatabaseSteps.query<Int, MinecraftVersionRange>(
            sql = SafeSQL.select("SELECT minecraft_version_range FROM ideas WHERE id = ?"),
            parameterSetter = { statement, ideaId ->
                statement.setInt(1, ideaId)
            },
            resultMapper = { resultSet ->
                resultSet.next()
                Json.decodeFromString(MinecraftVersionRange.serializer(), resultSet.getString("minecraft_version_range"))
            }
        ).process(input.second)

        if (worldVersion is Result.Failure) {
            return worldVersion
        }

        if (ideaVersion is Result.Failure) {
            return ideaVersion
        }

        return if (ideaVersion.getOrNull()!!.contains(worldVersion.getOrNull()!!)) {
            Result.Success(input)
        } else {
            Result.Failure(AppFailure.customValidationError("idea", "Idea is not compatible with the world's Minecraft version"))
        }
    }
}

data class BasicIdeaInfo(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val productionRate: Map<String, Int>
)

private val GetIdeaForImportStep = DatabaseSteps.transaction { connection ->
    object : Step<Int, AppFailure.DatabaseError, Pair<BasicIdeaInfo, Map<String, Int>>> {
        override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<BasicIdeaInfo, Map<String, Int>>> {
            val ideaInfo = DatabaseSteps.query<Int, BasicIdeaInfo>(
                sql = SafeSQL.select("""
                    SELECT id, name, description, category, category_data ->> 'productionRate' as production_rate
                    FROM ideas
                    WHERE id = ?
                """.trimIndent()),
                parameterSetter = { statement, id ->
                    statement.setInt(1, id)
                },
                resultMapper = { resultSet ->
                    resultSet.next()
                    BasicIdeaInfo(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        IdeaCategory.valueOf(resultSet.getString("category")),
                        extractProductionRates(
                            resultSet.getString("production_rate")
                                ?.let { Json.decodeFromString<CategoryValue>(it) as? CategoryValue.MapValue }?.value
                                ?: emptyMap()
                        )
                    )
                },
                transactionConnection = connection
            ).process(input)

            if (ideaInfo is Result.Failure) {
                return Result.Failure(ideaInfo.error)
            }

            val requirementInfo = DatabaseSteps.query<Int, Map<String, Int>>(
                sql = SafeSQL.select("SELECT item_id, quantity FROM idea_item_requirements WHERE idea_id = ?"),
                parameterSetter = { statement, ideaId ->
                    statement.setInt(1, ideaId)
                },
                resultMapper = {
                    buildMap {
                        while (it.next()) {
                            val itemId = it.getString("item_id")
                            val quantity = it.getInt("quantity")
                            this[itemId] = quantity
                        }
                    }
                }
            ).process(input)

            if (requirementInfo is Result.Failure) {
                return Result.Failure(requirementInfo.error)
            }

            return Result.Success(Pair(ideaInfo.getOrNull()!!, requirementInfo.getOrNull()!!))
        }
    }
}

private fun extractProductionRates(productionRateData: Map<String, CategoryValue>): Map<String, Int> {
    val productionRates = mutableMapOf<String, Int>()
    val modeMap = productionRateData["value"] as? CategoryValue.MapValue ?: return productionRates

    for (mode in modeMap.value.values) {
        if (mode is CategoryValue.MapValue) {
            for ((itemId, amountValue) in mode.value) {
                if (amountValue is CategoryValue.IntValue) {
                    productionRates[itemId] = (productionRates[itemId] ?: 0) // TODO: Support multiple production entries?
                }
            }
        }
    }

    return productionRates
}

private data class ValidateItemIdsStep(val availableIds: List<Item>) : Step<Pair<BasicIdeaInfo, Map<String, Int>>, AppFailure, IdeaForImport> {
    override suspend fun process(input: Pair<BasicIdeaInfo, Map<String, Int>>): Result<AppFailure, IdeaForImport> {
        val (ideaInfo, requirements) = input
        val mappedRequirements = mutableMapOf<Item, Int>()
        val mappedProduction = mutableMapOf<Item, Int>()
        val errors = mutableListOf<ValidationFailure>()

        for ((itemId, amount) in requirements) {
            // Air is dropped, not reported (MCO-305) — an idea recorded from a schematic can
            // carry millions of air cells, and nobody has ever wanted them on a gathering list.
            if (itemId in NON_MATERIAL_FILL) continue

            val item = availableIds.find { it.id == itemId }

            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("requirements", "Item ID $itemId is not available in the world version"))
            } else {
                mappedRequirements[item] = amount
            }
        }

        // TODO: Support multiple production entries?
        for ((itemId, amount) in ideaInfo.productionRate) {
            val item = availableIds.find { it.id == itemId }

            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("productionRate", "Item ID $itemId is not available in the world version"))
            } else {
                mappedProduction[item] = amount
            }
        }

        return if (errors.isEmpty()) {
            Result.Success(
                IdeaForImport(
                    id = ideaInfo.id,
                    name = ideaInfo.name,
                    description = ideaInfo.description,
                    category = ideaInfo.category,
                    requirements = mappedRequirements,
                    production = mappedProduction
                )
            )
        } else {
            Result.Failure(AppFailure.ValidationError(errors))
        }
    }
}

data class CreateDependencyInput(
    val projectId: Int,
    val taskId: Int,
    val dependsOnProjectId: Int
)

private data class CreateProjectFromIdeaStep(val worldId: Int, val taskId: Int?) : Step<IdeaForImport, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: IdeaForImport): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction { connection ->
            object : Step<IdeaForImport, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: IdeaForImport): Result<AppFailure.DatabaseError, Int> {
                    val projectIdResult = DatabaseSteps.update<IdeaForImport>(
                        sql = SafeSQL.insert("""
                            INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension, project_idea_id)
                            VALUES (?, ?, ?, ?, 'RESOURCE_GATHERING', 'ACTIVE', NULL, NULL, NULL, NULL, ?)
                            RETURNING id
                        """.trimIndent()),
                        parameterSetter = { statement, idea ->
                            statement.setInt(1, worldId)
                            statement.setString(2, idea.name)
                            statement.setString(3, idea.description)
                            statement.setString(4, idea.category.toProjectType().name)
                            statement.setInt(5, idea.id)
                        },
                        transactionConnection = connection
                    ).process(input)

                    if (projectIdResult is Result.Failure) {
                        return Result.Failure(projectIdResult.error)
                    }

                    val projectId = projectIdResult.getOrNull()!!

                    val reqs = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
                        SafeSQL.insert("""
                            INSERT INTO resource_gathering
                                (project_id, name, required, item_id)
                                values (?, ?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, idea ->
                            statement.setInt(1, projectId)
                            statement.setString(2, idea.first.name)
                            statement.setInt(3, idea.second)
                            statement.setString(4, idea.first.id)
                        },
                        transactionConnection = connection
                    ).process(input.requirements.toList())

                    if (reqs is Result.Failure) {
                        return Result.Failure(reqs.error)
                    }

                    val production = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
                        SafeSQL.insert("""
                            INSERT INTO project_productions
                                (project_id, name, item_id, rate_per_hour)
                                values (?, ?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, idea ->
                            statement.setInt(1, projectId)
                            statement.setString(2, idea.first.name)
                            statement.setString(3, idea.first.id)
                            statement.setInt(4, idea.second)
                        },
                        transactionConnection = connection
                    ).process(input.production.toList())

                    if (production is Result.Failure) {
                        return Result.Failure(production.error)
                    }

                    if (taskId != null) {
                        val projectOfTaskId = DatabaseSteps.query<Int, Int>(
                            sql = SafeSQL.select("SELECT project_id FROM resource_gathering WHERE id = ?"),
                            parameterSetter = { statement, tId ->
                                statement.setInt(1, tId)
                            },
                            resultMapper = { resultSet ->
                                resultSet.next()
                                resultSet.getInt("project_id")
                            },
                            transactionConnection = connection
                        ).process(taskId)

                        if (projectOfTaskId is Result.Failure) {
                            return projectOfTaskId
                        }
                        val dependencyResult = DatabaseSteps.update<CreateDependencyInput>(
                            sql = SafeSQL.insert("""
                                INSERT INTO project_dependencies (project_id, depends_on_project_id, tasks_depending_on_dependency_project) VALUES (?, ?, ?)
                            """.trimIndent()),
                            parameterSetter = { statement, input ->
                                statement.setInt(1, input.projectId)
                                statement.setInt(2, input.dependsOnProjectId)
                                statement.setArray(3, statement.connection.createArrayOf("INTEGER", arrayOf(input.taskId)))
                            },
                            transactionConnection = connection
                        ).process(CreateDependencyInput(
                            projectId = projectOfTaskId.getOrNull()!!,
                            taskId = taskId,
                            dependsOnProjectId = projectId
                        ))

                        if (dependencyResult is Result.Failure) {
                            return dependencyResult
                        }
                    }

                    return Result.Success(projectId)
                }
            }
        }.process(input)
    }

    private fun IdeaCategory.toProjectType(): ProjectType {
        return when (this) {
            IdeaCategory.BUILD -> ProjectType.BUILDING
            IdeaCategory.FARM -> ProjectType.FARMING
            IdeaCategory.STORAGE -> ProjectType.TECHNICAL
            IdeaCategory.CART_TECH -> ProjectType.TECHNICAL
            IdeaCategory.TNT -> ProjectType.TECHNICAL
            IdeaCategory.SLIMESTONE -> ProjectType.REDSTONE
            IdeaCategory.OTHER -> ProjectType.DECORATION
        }
    }
}
