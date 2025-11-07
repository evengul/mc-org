package app.mcorg.pipeline.world.settings.invitations

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.invitation.extractors.toInvite
import app.mcorg.pipeline.notification.commonsteps.CreateNotificationInput
import app.mcorg.pipeline.notification.commonsteps.CreateNotificationStep
import app.mcorg.pipeline.notification.commonsteps.NotificationTypes
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsResult
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.pipeline.world.settings.getStatusFromURL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.settings.worldInvitationTabs
import app.mcorg.presentation.templated.settings.worldInvite
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

data class CreateInvitationInput(
    val toUsername: String,
    val role: Role
)

data class CreateInvitationResult(
    val invitationId: Int,
    val worldId: Int,
    val worldName: String,
    val inviterName: String,
    val inviteeUserId: Int
)

suspend fun ApplicationCall.handleCreateInvitation() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    val selectedStatus = this.getStatusFromURL()

    executePipeline(
        onSuccess = {
            val mainContent = createHTML().div {
                hxOutOfBands("true")
                hxTarget("#invitation-status-filter")
                worldInvitationTabs(it.second, selectedStatus)
            }
            if ((selectedStatus == InvitationStatusFilter.PENDING) || selectedStatus == InvitationStatusFilter.ALL) {
                val addedInvite = createHTML().li {
                    worldInvite(it.first)
                }
                if ((selectedStatus == InvitationStatusFilter.PENDING && it.second.pending == 1) ||
                    (selectedStatus == InvitationStatusFilter.ALL && it.second.all == 1)) {
                    respondHtml(mainContent + addedInvite + createHTML().ul {
                        hxOutOfBands("delete:li#empty-invitations-list")
                        li { id = "empty-invitations-list" }
                    })
                }
                respondHtml(
                    mainContent + addedInvite
                )
            } else {
                respondHtml(mainContent)
            }
        },
    ) {
        value(parameters)
            .step(ValidateInvitationInputStep)
            .step(ValidateInviterPermissionsStep(user, worldId))
            .step(ValidateTargetUserStep)
            .step(ValidateNotSelfInviteStep(user.id))
            .step(CreateInvitationStep(user.id, worldId))
            .step(SendInvitationNotificationStep(user.minecraftUsername))
            .step(object : Step<CreateInvitationResult, AppFailure.DatabaseError, Pair<Invite, CountWorldInvitationsResult>> {
                override suspend fun process(input: CreateInvitationResult): Result<AppFailure.DatabaseError, Pair<Invite, CountWorldInvitationsResult>> {
                    val invite = GetInviteStep.process(input.invitationId)
                    if (invite is Result.Failure) {
                        return invite
                    }
                    val counts = CountWorldInvitationsStep(worldId).process(Unit)
                    if (counts is Result.Failure) {
                        return counts
                    }
                    return Result.success(Pair(invite.getOrNull()!!, counts.getOrNull()!!))
                }
            })
    }
}

// Step 1: Validate input parameters
object ValidateInvitationInputStep : Step<Parameters, AppFailure.ValidationError, CreateInvitationInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateInvitationInput> {
        val toUsername = ValidationSteps.required("toUsername") { AppFailure.ValidationError(listOf(it)) }
            .process(input)
        val roleString = ValidationSteps.required("role") { AppFailure.ValidationError(listOf(it)) }
            .process(input)

        val errors = mutableListOf<ValidationFailure>()
        if (toUsername is Result.Failure) errors.addAll(toUsername.error.errors)
        if (roleString is Result.Failure) errors.addAll(roleString.error.errors)

        val role = if (roleString is Result.Success) {
            val roleUppercase = roleString.getOrNull()?.uppercase()
            if (roleUppercase.isNullOrBlank()) {
                errors.add(ValidationFailure.MissingParameter("role"))
                null
            }
            else if (roleUppercase == "OWNER" || roleUppercase == "BANNED") {
                errors.add(ValidationFailure.InvalidFormat("role", "Can only invite as MEMBER or ADMIN"))
                null
            }
            else if (roleUppercase == "MEMBER" || roleUppercase == "ADMIN") {
                Role.valueOf(roleUppercase)
            } else {
                errors.add(ValidationFailure.InvalidFormat("role", "Invalid role specified"))
                null
            }
        } else null

        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            Result.success(CreateInvitationInput(toUsername.getOrNull()!!, role!!))
        }
    }
}

// Step 2: Validate inviter has admin+ permissions
class ValidateInviterPermissionsStep(
    private val user: TokenProfile,
    private val worldId: Int
) : Step<CreateInvitationInput, AppFailure, CreateInvitationInput> {
    override suspend fun process(input: CreateInvitationInput): Result<AppFailure, CreateInvitationInput> {
        // Query to check if user has Admin+ role in world
        val result = DatabaseSteps.query<CreateInvitationInput, Role?>(
            sql = SafeSQL.select("SELECT world_role FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, user.id)
            },
            resultMapper = { rs ->
                if (rs.next()) {
                    val userRole = Role.fromLevel(rs.getInt("world_role"))
                    userRole
                } else {
                    null // User not member of world
                }
            }
        ).process(input)

        return when (result) {
            is Result.Success -> {
                val userRole = result.value
                if (userRole == null || userRole.level > Role.ADMIN.level) {
                    Result.failure(AppFailure.AuthError.NotAuthorized)
                } else {
                    Result.success(input)
                }
            }
            is Result.Failure -> result
        }
    }
}

// Step 3: Validate target user exists
object ValidateTargetUserStep : Step<CreateInvitationInput, AppFailure, Pair<CreateInvitationInput, Int>> {
    override suspend fun process(input: CreateInvitationInput): Result<AppFailure, Pair<CreateInvitationInput, Int>> {
        return DatabaseSteps.query<CreateInvitationInput, Int?>(
            sql = SafeSQL.select("SELECT u.id FROM users u join minecraft_profiles mp on u.id = mp.user_id WHERE mp.username = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, input.toUsername)
            },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt("id")
                } else {
                    null
                }
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val targetUserId = result.value
                    if (targetUserId == null) {
                        Result.failure(AppFailure.customValidationError("toUsername", "User with username '${input.toUsername}' does not exist"))
                    } else {
                        Result.success(Pair(input, targetUserId))
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

// Step 4: Validate not inviting self
class ValidateNotSelfInviteStep(
    private val inviterUserId: Int
) : Step<Pair<CreateInvitationInput, Int>, AppFailure.ValidationError, Pair<CreateInvitationInput, Int>> {
    override suspend fun process(input: Pair<CreateInvitationInput, Int>): Result<AppFailure.ValidationError, Pair<CreateInvitationInput, Int>> {
        val (_, targetUserId) = input
        return if (inviterUserId == targetUserId) {
            Result.failure(AppFailure.customValidationError("toUsername", "Cannot invite yourself to a world"))
        } else {
            Result.success(input)
        }
    }
}

// Step 5: Create invitation in database
class CreateInvitationStep(
    private val fromUserId: Int,
    private val worldId: Int
) : Step<Pair<CreateInvitationInput, Int>, AppFailure, CreateInvitationResult> {
    override suspend fun process(input: Pair<CreateInvitationInput, Int>): Result<AppFailure, CreateInvitationResult> {
        val (invitationInput, toUserId) = input

        val result =  DatabaseSteps.update<Pair<CreateInvitationInput, Int>>(
            sql = SafeSQL.insert("""
                INSERT INTO invites (world_id, from_user_id, to_user_id, role, created_at, status, status_reached_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'PENDING', CURRENT_TIMESTAMP)
                RETURNING id
            """.trimIndent()),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, fromUserId)
                stmt.setInt(3, toUserId)
                stmt.setString(4, invitationInput.role.name)
            }
        ).process(input)

        if (result is Result.Failure) {
            if (result.error is AppFailure.DatabaseError.IntegrityConstraintError) {
                // Check if it's a unique constraint violation on (world_id, to_user_id) to indicate existing pending invite
                return Result.failure(
                    AppFailure.customValidationError(
                        "toUsername",
                        "This user already has a pending invitation to this world"
                    )
                )
            }
            return result
        }

        return result.flatMap { invitationId ->
            // Fetch world name for notification
            DatabaseSteps.query<Int, String>(
                sql = SafeSQL.select("SELECT name FROM world WHERE id = ?"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, worldId)
                },
                resultMapper = { rs ->
                    if (rs.next()) {
                        rs.getString("name")
                    } else {
                        "Unknown World"
                    }
                }
            ).process(invitationId).map { worldName ->
                CreateInvitationResult(invitationId, worldId, worldName, "", toUserId)
            }
        }
    }
}

// Step 6: Send invitation notification
class SendInvitationNotificationStep(
    private val inviterUsername: String
) : Step<CreateInvitationResult, AppFailure.DatabaseError, CreateInvitationResult> {
    override suspend fun process(input: CreateInvitationResult): Result<AppFailure.DatabaseError, CreateInvitationResult> {
        // Create notification for the invitee
        val notificationInput = CreateNotificationInput(
            userId = input.inviteeUserId,
            title = "World Invitation Received",
            description = "$inviterUsername has invited you to join the world \"${input.worldName}\"",
            type = NotificationTypes.INVITATION_RECEIVED,
            link = "/app"
        )

        // Send notification
        return CreateNotificationStep.process(notificationInput).let { result ->
            when (result) {
                is Result.Success -> Result.success(input.copy(inviterName = inviterUsername))
                is Result.Failure -> result
            }
        }
    }
}

object GetInviteStep : Step<Int, AppFailure.DatabaseError, Invite> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Invite> {
        return DatabaseSteps.query<Int, Invite?>(
            sql = SafeSQL.select("""
                SELECT 
                    i.id,
                    i.world_id,
                    w.name as world_name,
                    i.from_user_id,
                    mp_from.username as from_username,
                    i.to_user_id,
                    mp_to.username as to_username,
                    i.role,
                    i.created_at,
                    i.created_at + INTERVAL '7 days' as expires_at,
                    i.status,
                    i.status_reached_at
                FROM invites i
                JOIN world w ON i.world_id = w.id
                JOIN users u_from ON i.from_user_id = u_from.id
                JOIN minecraft_profiles mp_from ON u_from.id = mp_from.user_id
                JOIN users u_to ON i.to_user_id = u_to.id
                JOIN minecraft_profiles mp_to ON u_to.id = mp_to.user_id
                WHERE i.id = ?
            """.trimIndent()),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, input)
            },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.toInvite()
                } else {
                    null
                }
            }).process(input).flatMap {
            if (it == null) {
                Result.failure(AppFailure.DatabaseError.NotFound)
            } else {
                Result.success(it)
            }
        }
    }
}
