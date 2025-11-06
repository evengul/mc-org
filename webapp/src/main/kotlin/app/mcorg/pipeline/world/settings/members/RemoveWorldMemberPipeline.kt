package app.mcorg.pipeline.world.settings.members

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldMemberId
import app.mcorg.presentation.utils.respondEmptyHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleRemoveWorldMember() {
    val currentUserId = this.getUser().id
    val worldId = this.getWorldId()
    val memberId = this.getWorldMemberId()

    executePipeline(
        onSuccess = { respondEmptyHtml() },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to remove world member") }
    ) {
        step(Step.value(Unit))
            .step(ValidateWorldMemberRemovalAllowedStep(
                worldId = worldId,
                currentUserId = currentUserId,
                removedMemberId = memberId
            ))
            .step(RemoveWorldMemberStep(
                worldId = worldId,
                memberId = memberId
            ))
    }
}

private data class ValidateWorldMemberRemovalAllowedStep(
    val worldId: Int,
    val currentUserId: Int,
    val removedMemberId: Int
) : Step<Unit, AppFailure, Unit> {
    override suspend fun process(input: Unit): Result<AppFailure, Unit> {
        val result = DatabaseSteps.query<Unit, Boolean>(
            SafeSQL.select("SELECT 1 FROM world_members WHERE world_id = ? AND user_id = ? AND world_role < (SELECT world_role FROM world_members where user_id = ? AND world_role > 0 AND world_id = ?)"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, currentUserId)
                statement.setInt(3, removedMemberId)
                statement.setInt(4, worldId)
            },
            resultMapper = { it.next() }
        ).process(input)

        return when(result) {
            is Result.Success -> {
                if (result.value) {
                    Result.success()
                } else {
                    Result.failure(AppFailure.AuthError.NotAuthorized)
                }
            }
            is Result.Failure -> result
        }
    }
}

private data class RemoveWorldMemberStep(
    val worldId: Int,
    val memberId: Int
) : Step<Unit, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Unit>(
            SafeSQL.delete("DELETE FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId); statement.setInt(2, memberId) },
        ).process(input).map { }
    }
}