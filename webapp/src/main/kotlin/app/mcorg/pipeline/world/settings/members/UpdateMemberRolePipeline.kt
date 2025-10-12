package app.mcorg.pipeline.world.settings.members

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldMemberId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond

sealed interface UpdateMemberRoleFailure {
    data class ValidationError(val error: ValidationFailure) : UpdateMemberRoleFailure
    data class DatabaseError(val error: DatabaseFailure) : UpdateMemberRoleFailure
    object IllegalRoleChange : UpdateMemberRoleFailure
}

suspend fun ApplicationCall.handleUpdateWorldMemberRole() {
    val currentUserId = this.getUser().id
    val worldId = this.getWorldId()
    val memberId = this.getWorldMemberId()

    val parameters = receiveParameters()

    executePipeline(
        onSuccess = { respondHtml(it.toPrettyEnumName()) },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to update world member role") },
    ) {
        step(Step.value(parameters))
            .step(ValidateWorldMemberRoleInputStep)
            .step(ValidateWorldMemberRoleChangeAllowedStep(worldId, currentUserId, memberId))
            .step(UpdateWorldMemberRoleStep(worldId, memberId))
    }
}

private object ValidateWorldMemberRoleInputStep : Step<Parameters, UpdateMemberRoleFailure.ValidationError, Role> {
    override suspend fun process(input: Parameters): Result<UpdateMemberRoleFailure.ValidationError, Role> {
        return ValidationSteps.required("role") { it }
            .process(input)
            .flatMap {
                ValidationSteps.validateAllowedValues(
                    "role",
                    Role.entries.filter { role -> role != Role.BANNED && role != Role.OWNER }.map { role -> role.name },
                    { e -> e },
                    ignoreCase = true
                ).process(it)
            }.map { Role.valueOf(it) }
            .mapError { UpdateMemberRoleFailure.ValidationError(it) }
    }
}

data class ValidateWorldMemberRoleChangeAllowedStep(val worldId: Int, val currentUserId: Int, val changingMemberId: Int) : Step<Role, UpdateMemberRoleFailure, Role> {
    override suspend fun process(input: Role): Result<UpdateMemberRoleFailure, Role> {
        val result = DatabaseSteps.query<Role, UpdateMemberRoleFailure, Boolean>(
            SafeSQL.select("SELECT 1 FROM world_members WHERE world_id = ? AND user_id = ? AND world_role < (SELECT world_role FROM world_members where user_id = ? AND world_id = ?)"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, currentUserId)
                statement.setInt(3, changingMemberId)
                statement.setInt(4, worldId)
            },
            errorMapper = { UpdateMemberRoleFailure.DatabaseError(it) },
            resultMapper = { it.next() }
        ).process(input)

        return when(result) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                if (result.value) {
                    Result.Success(input)
                } else {
                    Result.Failure(UpdateMemberRoleFailure.IllegalRoleChange)
                }
            }
        }
    }
}

private data class UpdateWorldMemberRoleStep(
    val worldId: Int,
    val memberId: Int
) : Step<Role, UpdateMemberRoleFailure.DatabaseError, Role> {
    override suspend fun process(input: Role): Result<UpdateMemberRoleFailure.DatabaseError, Role> {
        return DatabaseSteps.update<Role, UpdateMemberRoleFailure.DatabaseError>(
            SafeSQL.update("UPDATE world_members SET world_role = ? WHERE world_id = ? AND user_id = ? RETURNING world_role"),
            parameterSetter = { statement, role ->
                statement.setInt(1, role.level)
                statement.setInt(2, worldId)
                statement.setInt(3, memberId)
            },
            errorMapper = { UpdateMemberRoleFailure.DatabaseError(it) }
        )
            .process(input)
            .map { Role.fromLevel(it) }
    }
}