package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.home.worldsView
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCreateWorld() {
    val parameters = this.receiveParameters()
    val user = this.getUser()

    executePipeline(
        onSuccess = {
            val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
            respondHtml(createHTML().div {
                worldsView(user, it, supportedVersions)
            })
        }
    ) {
        value(parameters)
            .step(ValidateWorldInputStep)
            .step(CreateWorldStep(user))
            .map { GetPermittedWorldsInput(
                userId = user.id
            ) }
            .step(GetPermittedWorldsStep)
    }
}

object ValidateWorldInputStep : Step<Parameters, AppFailure.ValidationError, CreateWorldInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateWorldInput> {
        val nameResult = ValidationSteps.required("name") { listOf(it) }.process(input)
            .flatMap { name -> ValidationSteps.validateLength("name", 3, 100) { listOf(it) }.process(name) }

        val descriptionResult = input["description"]?.let { desc ->
            ValidationSteps.validateLength("description", 0, 500) { listOf(it) }.process(desc)
        }
        val versionResult = ValidationSteps.validateCustom<List<ValidationFailure>, String?>(
            "version",
            "Invalid Minecraft version",
            errorMapper = { listOf(it) },
            predicate = {
                !it.isNullOrBlank() && runCatching {
                    MinecraftVersion.fromString(it)
                }.isSuccess
            }).process(input["version"]).map { MinecraftVersion.fromString(it!!) }

        val errors = mutableListOf<ValidationFailure>()
        if (nameResult is Result.Failure) {
            errors.addAll(nameResult.error)
        }
        if (descriptionResult is Result.Failure) {
            errors.addAll(descriptionResult.error)
        }
        if (versionResult is Result.Failure) {
            errors.addAll(versionResult.error)
        }

        if (errors.isNotEmpty()) {
            return Result.failure(AppFailure.ValidationError(errors.toList()))
        }

        return Result.success(
            CreateWorldInput(
                name = nameResult.getOrNull()!!,
                description = descriptionResult?.getOrNull() ?: "",
                version = versionResult.getOrNull()!!
            )
        )
    }
}

data class CreateWorldInput(
    val name: String,
    val description: String,
    val version: MinecraftVersion
)

data class CreateWorldStep(val user: TokenProfile) : Step<CreateWorldInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateWorldInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction { connection ->
            object : Step<CreateWorldInput, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: CreateWorldInput): Result<AppFailure.DatabaseError, Int> {
                    return DatabaseSteps.update<CreateWorldInput>(
                        SafeSQL.insert("INSERT INTO world (name, description, version) VALUES (?, ?, ?) RETURNING id"),
                        parameterSetter = { parameters, i ->
                            parameters.setString(1, i.name)
                            parameters.setString(2, i.description)
                            parameters.setString(3, i.version.toString())
                        },
                        connection
                    ).process(input).flatMap { previousResult ->
                        DatabaseSteps.update<Int>(
                            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                            parameterSetter = { parameters, worldId ->
                                parameters.setInt(1, user.id)
                                parameters.setInt(2, worldId)
                                parameters.setString(3, user.minecraftUsername)
                                parameters.setInt(4, Role.OWNER.level)
                            },
                            connection
                        ).process(previousResult).map { previousResult }
                    }
                }
            }
        }.process(input)
    }
}