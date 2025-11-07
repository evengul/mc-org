package app.mcorg.pipeline.invitation

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationAccessStep
import app.mcorg.pipeline.invitation.commonsteps.ValidateInvitationPendingStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getInviteId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

data class AcceptInvitationContext(
    val userId: Int,
    val inviteId: Int,
    val worldId: Int,
    val worldName: String,
)

suspend fun ApplicationCall.handleAcceptInvitation() {
    val user = this.getUser()
    val inviteId = this.getInviteId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    + "Invitation accepted! You've joined ${it}."
                }
            })
        }
    ) {
        value(inviteId)
            .step(ValidateInvitationAccessStep(user.id))
            .map { AcceptInvitationContext(
                userId = user.id,
                inviteId = inviteId,
                worldId = it.first,
                worldName = it.second
            ) }
            .map { inviteId to it }
            .step(ValidateInvitationPendingStep())
            .step(CheckNotAlreadyMemberStep)
            .step(AcceptInvitationStep)
            .map { it.worldName }
    }
}

private object CheckNotAlreadyMemberStep : Step<AcceptInvitationContext, AppFailure, AcceptInvitationContext> {
    override suspend fun process(input: AcceptInvitationContext): Result<AppFailure, AcceptInvitationContext> {
        return DatabaseSteps.query<AcceptInvitationContext, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) as member_count FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { stmt, (userId, _, worldId, _) ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, userId)
            },
            resultMapper = { rs ->
                rs.next()
                rs.getInt("member_count")
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val memberCount = result.value
                    if (memberCount > 0) {
                        Result.failure(AppFailure.ValidationError(listOf(
                            ValidationFailure.CustomValidation("user", "User is already a member of the world")
                        )))
                    } else {
                        Result.success(input)
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

private object AcceptInvitationStep : Step<AcceptInvitationContext, AppFailure, AcceptInvitationContext> {
    override suspend fun process(input: AcceptInvitationContext): Result<AppFailure, AcceptInvitationContext> {
        return DatabaseSteps.transaction { connection ->
            object : Step<AcceptInvitationContext, AppFailure.DatabaseError, AcceptInvitationContext> {
                override suspend fun process(input: AcceptInvitationContext): Result<AppFailure.DatabaseError, AcceptInvitationContext> {
                    // Update invitation status to ACCEPTED
                    val updateInviteResult = DatabaseSteps.update<Int>(
                        sql = SafeSQL.update("UPDATE invites SET status = 'ACCEPTED', status_reached_at = CURRENT_TIMESTAMP WHERE id = ?"),
                        parameterSetter = { stmt, inviteId ->
                            stmt.setInt(1, inviteId)
                        },
                        connection
                    ).process(input.inviteId)

                    if (updateInviteResult is Result.Failure) {
                        return updateInviteResult
                    }

                    val usernameResult = DatabaseSteps.query<Int, Result<AppFailure.DatabaseError, String>>(
                        sql = SafeSQL.select("SELECT username FROM minecraft_profiles WHERE id = ?"),
                        parameterSetter = { stmt, userId ->
                            stmt.setInt(1, userId)
                        },
                        resultMapper = { rs ->
                            rs.next()
                            Result.success(rs.getString("username"))
                        },
                        connection
                    ).process(input.userId).flatMap { it }

                    if (usernameResult is Result.Failure) {
                        return usernameResult
                    }

                    val username = (usernameResult as Result.Success<String>).value

                    val inviteRole = DatabaseSteps.query<Int, String>(
                        sql = SafeSQL.select("SELECT role FROM invites WHERE id = ?"),
                        parameterSetter = { stmt, inviteId ->
                            stmt.setInt(1, inviteId)
                        },
                        resultMapper = { rs ->
                            rs.next()
                            rs.getString("role")
                        },
                        connection
                    ).process(input.inviteId).map { Role.valueOf(it) }

                    if (inviteRole is Result.Failure) {
                        return inviteRole
                    }

                    // Create world membership
                    val createMembershipResult = DatabaseSteps.update<AcceptInvitationContext>(
                        sql = SafeSQL.insert("""
                            INSERT INTO world_members (world_id, user_id, display_name, world_role, created_at, updated_at)
                            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.trimIndent()),
                        parameterSetter = { stmt, (userId, _, worldId, _) ->
                            stmt.setInt(1, worldId)
                            stmt.setInt(2, userId)
                            stmt.setString(3, username)
                            stmt.setInt(4, inviteRole.getOrNull()!!.level)
                        },
                        connection
                    ).process(input)

                    return when (createMembershipResult) {
                        is Result.Success -> Result.success(input)
                        is Result.Failure -> createMembershipResult
                    }
                }
            }
        }.process(input)
    }
}