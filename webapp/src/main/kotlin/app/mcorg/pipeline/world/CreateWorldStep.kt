package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.CreateWorldFailures

data class CreateWorldInput(
    val name: String,
    val description: String,
    val version: MinecraftVersion
)

data class CreateWorldStep(val user: TokenProfile) : Step<CreateWorldInput, CreateWorldFailures.DatabaseError, Int> {
    override suspend fun process(input: CreateWorldInput): Result<CreateWorldFailures.DatabaseError, Int> {
        return DatabaseSteps.transaction(
            object : Step<CreateWorldInput, CreateWorldFailures.DatabaseError, Int> {
                override suspend fun process(input: CreateWorldInput): Result<CreateWorldFailures.DatabaseError, Int> {
                    return DatabaseSteps.update<CreateWorldInput, CreateWorldFailures.DatabaseError>(
                        SafeSQL.insert("INSERT INTO world (name, description, version) VALUES (?, ?, ?) RETURNING id"),
                        parameterSetter = { parameters, i ->
                            parameters.setString(1, i.name)
                            parameters.setString(2, i.description)
                            parameters.setString(3, i.version.toString())
                        },
                        errorMapper = { CreateWorldFailures.DatabaseError }
                    ).process(input).flatMap {
                        DatabaseSteps.update<Int, CreateWorldFailures.DatabaseError>(
                            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                            parameterSetter = { parameters, worldId ->
                                parameters.setInt(1, user.id)
                                parameters.setInt(2, it)
                                parameters.setString(3, user.minecraftUsername)
                                parameters.setInt(4, Role.OWNER.level)
                            },
                            errorMapper = { CreateWorldFailures.DatabaseError }
                        ).process(it)
                        return@flatMap Result.success(it)
                    }
                }
            },
            errorMapper = { CreateWorldFailures.DatabaseError }
        ).process(input)
    }
}
