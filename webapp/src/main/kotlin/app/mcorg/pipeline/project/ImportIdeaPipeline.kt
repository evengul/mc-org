package app.mcorg.pipeline.project

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.extractors.deserializeVersionRange
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json

data class IdeaForImport(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val requirements: Map<Item, Int>,
    val production: Map<Item, Int>
)

suspend fun ApplicationCall.handleImportIdea() {
    val ideaId = this.getIdeaId()

    val worldId = (this.receiveParameters()["worldId"] ?: parameters["worldId"])?.toIntOrNull()
        ?: return run {
            defaultHandleError(AppFailure.customValidationError("worldId", "Invalid or missing worldId parameter"))
        }

    val taskId = parameters["forTask"]?.toIntOrNull()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    executePipeline(
        onSuccess = { clientRedirect(Link.Worlds.world(worldId).project(it).to) }
    ) {
        value(worldId to ideaId)
            .step(ValidateVersionRangeStep)
            .value(ideaId)
            .step(GetIdeaForImportStep)
            .step(ValidateItemIdsStep(items))
            .step(CreateProjectFromIdeaStep(worldId, taskId))
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
                deserializeVersionRange(resultSet.getString("minecraft_version_range"))
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
    val productionRate: Map<String, Map<String, Int>>
)

private val GetIdeaForImportStep = DatabaseSteps.transaction { connection ->
    object : Step<Int, AppFailure.DatabaseError, Pair<BasicIdeaInfo, Map<String, Int>>> {
        override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<BasicIdeaInfo, Map<String, Int>>> {
            val ideaInfo = DatabaseSteps.query<Int, BasicIdeaInfo>(
                sql = SafeSQL.select("""
                    SELECT id, name, description, category, category_data -> 'productionRate' as production_rate
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
                        resultSet.getString("production_rate")?.let { Json.decodeFromString(it) } ?: emptyMap()
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

private data class ValidateItemIdsStep(val availableIds: List<Item>) : Step<Pair<BasicIdeaInfo, Map<String, Int>>, AppFailure, IdeaForImport> {
    override suspend fun process(input: Pair<BasicIdeaInfo, Map<String, Int>>): Result<AppFailure, IdeaForImport> {
        val (ideaInfo, requirements) = input
        val mappedRequirements = mutableMapOf<Item, Int>()
        val mappedProduction = mutableMapOf<Item, Int>()
        val errors = mutableListOf<ValidationFailure>()

        for ((itemId, amount) in requirements) {
            val item = availableIds.find { it.id == itemId }

            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("requirements", "Item ID $itemId is not available in the world version"))
            } else {
                mappedRequirements[item] = amount
            }
        }

        // TODO: Support multiple production entries?
        for ((_, amounts) in ideaInfo.productionRate) {
            for ((itemId, amount) in amounts) {
                val item = availableIds.find { it.id == itemId }

                if (item == null) {
                    errors.add(ValidationFailure.CustomValidation("productionRate", "Item ID $itemId is not available in the world version"))
                } else {
                    mappedProduction[item] = amount
                }
            }
            break
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
                            INSERT INTO projects (world_id, name, description, type, stage, location_x, location_y, location_z, location_dimension, project_idea_id) 
                            VALUES (?, ?, ?, ?, 'RESOURCE_GATHERING', 0, 0, 0, 'OVERWORLD', ?) 
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
                            INSERT INTO tasks 
                                (project_id, name, description, stage, priority, requirement_type, requirement_item_required_amount, requirement_item_collected, item_id) 
                                values (?, ?, '', 'RESOURCE_GATHERING', 'MEDIUM', 'ITEM', ?, 0, ?)
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
                            sql = SafeSQL.select("SELECT project_id FROM tasks WHERE id = ?"),
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


