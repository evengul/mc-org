package app.mcorg.pipeline.world.settings.members

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldMemberId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleUpdateWorldMemberRole() {
    val currentUserId = this.getUser().id
    val worldId = this.getWorldId()
    val memberId = this.getWorldMemberId()

    val parameters = receiveParameters()

    executePipeline(
        onSuccess = { respondHtml(it.toPrettyEnumName()) },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to update world member role") },
    ) {
        value(parameters)
            .step(ValidateWorldMemberRoleInputStep)
            .step(ValidateWorldMemberRoleChangeAllowedStep(worldId, currentUserId, memberId))
            .step(UpdateWorldMemberRoleStep(worldId, memberId))
    }
}

private object ValidateWorldMemberRoleInputStep : Step<Parameters, AppFailure.ValidationError, Role> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Role> {
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
            .mapError { AppFailure.ValidationError(listOf(it)) }
    }
}

data class ValidateWorldMemberRoleChangeAllowedStep(val worldId: Int, val currentUserId: Int, val changingMemberId: Int) : Step<Role, AppFailure, Role> {
    override suspend fun process(input: Role): Result<AppFailure, Role> {
        val result = DatabaseSteps.query<Role, Boolean>(
            SafeSQL.select("SELECT 1 FROM world_members WHERE world_id = ? AND user_id = ? AND world_role < (SELECT world_role FROM world_members where user_id = ? AND world_id = ?)"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, currentUserId)
                statement.setInt(3, changingMemberId)
                statement.setInt(4, worldId)
            },
            resultMapper = { it.next() }
        ).process(input)

        return when(result) {
            is Result.Failure -> result
            is Result.Success -> {
                if (result.value) {
                    Result.Success(input)
                } else {
                    Result.Failure(AppFailure.customValidationError("role", "You do not have permission to change this member's role"))
                }
            }
        }
    }
}

private data class UpdateWorldMemberRoleStep(
    val worldId: Int,
    val memberId: Int
) : Step<Role, AppFailure.DatabaseError, Role> {
    override suspend fun process(input: Role): Result<AppFailure.DatabaseError, Role> {
        return DatabaseSteps.update<Role>(
            SafeSQL.update("UPDATE world_members SET world_role = ? WHERE world_id = ? AND user_id = ? RETURNING world_role"),
            parameterSetter = { statement, role ->
                statement.setInt(1, role.level)
                statement.setInt(2, worldId)
                statement.setInt(3, memberId)
            }
        )
            .process(input)
            .map { Role.fromLevel(it) }
    }
}