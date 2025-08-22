package app.mcorg.presentation.handler

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

// Failure types for invitation operations
sealed interface InviteFailures {
    data object DatabaseError : InviteFailures
    data object InvitationNotFound : InviteFailures
    data object InvitationNotPending : InviteFailures
    data object UnauthorizedAccess : InviteFailures
    data object AlreadyWorldMember : InviteFailures
}

// Data classes for invitation operations
data class InviteOperationInput(
    val inviteId: Int,
    val userId: Int
)

data class AcceptInviteResult(
    val worldId: Int,
    val worldName: String,
    val role: Role
)

data class DeclineInviteResult(
    val inviteId: Int
)

class InviteHandler {
    fun Route.inviteRoutes() {
        route("/invites/{inviteId}") {
            patch("/accept") {
                call.handleAcceptInvitation()
            }
            patch("/decline") {
                call.handleDeclineInvitation()
            }
        }
    }
}

suspend fun ApplicationCall.handleAcceptInvitation() {
    val user = this.getUser()
    val inviteId = this.parameters["inviteId"]?.toIntOrNull() ?: return respondBadRequest("Invalid invitation ID")

    executePipeline(
        onSuccess = { result: AcceptInviteResult ->
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    +"Invitation accepted! You've joined ${result.worldName} as ${result.role.name.lowercase()}"
                }
            })
        },
        onFailure = { failure: InviteFailures ->
            val errorMessage = when (failure) {
                is InviteFailures.DatabaseError ->
                    "Unable to accept invitation: Database error"
                is InviteFailures.InvitationNotFound ->
                    "Invitation not found or has expired"
                is InviteFailures.InvitationNotPending ->
                    "This invitation has already been processed"
                is InviteFailures.UnauthorizedAccess ->
                    "You are not authorized to accept this invitation"
                is InviteFailures.AlreadyWorldMember ->
                    "You are already a member of this world"
            }
            respondBadRequest(errorMessage)
        }
    ) {
        step(Step.value(InviteOperationInput(inviteId, user.id)))
            .step(ValidateInvitationAccessStep)
            .step(ValidateInvitationPendingStep)
            .step(CheckNotAlreadyMemberStep)
            .step(AcceptInvitationStep)
    }
}

suspend fun ApplicationCall.handleDeclineInvitation() {
    val user = this.getUser()
    val inviteId = this.parameters["inviteId"]?.toIntOrNull() ?: return respondBadRequest("Invalid invitation ID")

    executePipeline(
        onSuccess = { result: DeclineInviteResult ->
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    +"Invitation declined successfully"
                }
                div {
                    id = "invite-${result.inviteId}"
                }
            })
        },
        onFailure = { failure: InviteFailures ->
            val errorMessage = when (failure) {
                is InviteFailures.DatabaseError ->
                    "Unable to decline invitation: Database error"
                is InviteFailures.InvitationNotFound ->
                    "Invitation not found or has expired"
                is InviteFailures.InvitationNotPending ->
                    "This invitation has already been processed"
                is InviteFailures.UnauthorizedAccess ->
                    "You are not authorized to decline this invitation"
                is InviteFailures.AlreadyWorldMember ->
                    "You are already a member of this world"
            }
            respondBadRequest(errorMessage)
        }
    ) {
        step(Step.value(InviteOperationInput(inviteId, user.id)))
            .step(ValidateInvitationAccessStep)
            .step(ValidateInvitationPendingStep)
            .step(DeclineInvitationStep)
    }
}

// Step 1: Validate user can access this invitation (invitation is for them)
object ValidateInvitationAccessStep : Step<InviteOperationInput, InviteFailures, Triple<InviteOperationInput, Int, String>> {
    override suspend fun process(input: InviteOperationInput): Result<InviteFailures, Triple<InviteOperationInput, Int, String>> {
        return DatabaseSteps.query<InviteOperationInput, InviteFailures, Pair<Int, String>?>(
            sql = SafeSQL.select("""
                SELECT i.world_id, w.name as world_name 
                FROM invites i 
                JOIN world w ON i.world_id = w.id 
                WHERE i.id = ? AND i.to_user_id = ?
            """.trimIndent()),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, input.inviteId)
                stmt.setInt(2, input.userId)
            },
            errorMapper = { InviteFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    Pair(rs.getInt("world_id"), rs.getString("world_name"))
                } else {
                    null
                }
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val worldInfo = result.value
                    if (worldInfo == null) {
                        Result.failure(InviteFailures.InvitationNotFound)
                    } else {
                        Result.success(Triple(input, worldInfo.first, worldInfo.second))
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

// Step 2: Validate invitation is still pending
object ValidateInvitationPendingStep : Step<Triple<InviteOperationInput, Int, String>, InviteFailures, Pair<Triple<InviteOperationInput, Int, String>, Role>> {
    override suspend fun process(input: Triple<InviteOperationInput, Int, String>): Result<InviteFailures, Pair<Triple<InviteOperationInput, Int, String>, Role>> {
        val (inviteInput, _, _) = input
        return DatabaseSteps.query<Triple<InviteOperationInput, Int, String>, InviteFailures, Pair<String, Role>?>(
            sql = SafeSQL.select("SELECT status, role FROM invites WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, inviteInput.inviteId)
            },
            errorMapper = { InviteFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    Pair(rs.getString("status"), Role.valueOf(rs.getString("role")))
                } else {
                    null
                }
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val inviteInfo = result.value
                    if (inviteInfo == null) {
                        Result.failure(InviteFailures.InvitationNotFound)
                    } else if (inviteInfo.first != "PENDING") {
                        Result.failure(InviteFailures.InvitationNotPending)
                    } else {
                        Result.success(Pair(input, inviteInfo.second))
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

// Step 3: Check user is not already a member of the world
object CheckNotAlreadyMemberStep : Step<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures, Pair<Triple<InviteOperationInput, Int, String>, Role>> {
    override suspend fun process(input: Pair<Triple<InviteOperationInput, Int, String>, Role>): Result<InviteFailures, Pair<Triple<InviteOperationInput, Int, String>, Role>> {
        val (inviteData, _) = input
        val (inviteInput, worldId, _) = inviteData

        return DatabaseSteps.query<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) as member_count FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, inviteInput.userId)
            },
            errorMapper = { InviteFailures.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getInt("member_count")
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val memberCount = result.value
                    if (memberCount > 0) {
                        Result.failure(InviteFailures.AlreadyWorldMember)
                    } else {
                        Result.success(input)
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

// Step 4: Accept invitation (update status and create world membership)
object AcceptInvitationStep : Step<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures, AcceptInviteResult> {
    override suspend fun process(input: Pair<Triple<InviteOperationInput, Int, String>, Role>): Result<InviteFailures, AcceptInviteResult> {
        val (inviteData, role) = input
        val (inviteInput, worldId, worldName) = inviteData

        return DatabaseSteps.transaction(
            step = object : Step<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures, AcceptInviteResult> {
                override suspend fun process(input: Pair<Triple<InviteOperationInput, Int, String>, Role>): Result<InviteFailures, AcceptInviteResult> {
                    // Update invitation status to ACCEPTED
                    val updateInviteResult = DatabaseSteps.update<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures.DatabaseError>(
                        sql = SafeSQL.update("UPDATE invites SET status = 'ACCEPTED', status_reached_at = CURRENT_TIMESTAMP WHERE id = ?"),
                        parameterSetter = { stmt, _ ->
                            stmt.setInt(1, inviteInput.inviteId)
                        },
                        errorMapper = { InviteFailures.DatabaseError }
                    ).process(input)

                    if (updateInviteResult is Result.Failure) {
                        return updateInviteResult
                    }

                    val usernameResult = DatabaseSteps.query<Int, InviteFailures.DatabaseError, Result<InviteFailures.DatabaseError, String>>(
                        sql = SafeSQL.select("SELECT username FROM minecraft_profiles WHERE id = ?"),
                        parameterSetter = { stmt, _ ->
                            stmt.setInt(1, inviteInput.userId)
                        },
                        errorMapper = { InviteFailures.DatabaseError },
                        resultMapper = { rs ->
                            if (rs.next()) {
                                Result.success(rs.getString("username"))
                            } else {
                                Result.failure(InviteFailures.DatabaseError)
                            }
                        }
                    ).process(input.first.first.userId).flatMap { it }

                    if (usernameResult is Result.Failure) {
                        return Result.failure(InviteFailures.DatabaseError)
                    }

                    val username = (usernameResult as Result.Success<String>).value

                    // Create world membership
                    val createMembershipResult = DatabaseSteps.update<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures.DatabaseError>(
                        sql = SafeSQL.insert("""
                            INSERT INTO world_members (world_id, user_id, display_name, world_role, created_at, updated_at)
                            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.trimIndent()),
                        parameterSetter = { stmt, _ ->
                            stmt.setInt(1, worldId)
                            stmt.setInt(2, inviteInput.userId)
                            stmt.setString(3, username)
                            stmt.setInt(4, role.level)
                        },
                        errorMapper = { InviteFailures.DatabaseError }
                    ).process(input)

                    return when (createMembershipResult) {
                        is Result.Success -> Result.success(AcceptInviteResult(worldId, worldName, role))
                        is Result.Failure -> createMembershipResult
                    }
                }
            },
            errorMapper = { InviteFailures.DatabaseError }
        ).process(input)
    }
}

// Step 4 (alternative): Decline invitation (update status only)
object DeclineInvitationStep : Step<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures, DeclineInviteResult> {
    override suspend fun process(input: Pair<Triple<InviteOperationInput, Int, String>, Role>): Result<InviteFailures, DeclineInviteResult> {
        val (inviteData, _) = input
        val (inviteInput, _, _) = inviteData

        return DatabaseSteps.update<Pair<Triple<InviteOperationInput, Int, String>, Role>, InviteFailures.DatabaseError>(
            sql = SafeSQL.update("UPDATE invites SET status = 'DECLINED', status_reached_at = CURRENT_TIMESTAMP WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, inviteInput.inviteId)
            },
            errorMapper = { InviteFailures.DatabaseError }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> Result.success(DeclineInviteResult(inviteInput.inviteId))
                is Result.Failure -> result
            }
        }
    }
}