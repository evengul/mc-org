package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.notification.CreateNotificationInput
import app.mcorg.pipeline.notification.CreateNotificationStep
import app.mcorg.pipeline.notification.NotificationTypes
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.stream.createHTML

sealed interface CreateInvitationFailures {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateInvitationFailures
    data object DatabaseError : CreateInvitationFailures
    data object InsufficientPermissions : CreateInvitationFailures
    data object UserNotFound : CreateInvitationFailures
    data object DuplicateInvitation : CreateInvitationFailures
    data object CannotInviteSelf : CreateInvitationFailures
    data object NotificationError : CreateInvitationFailures
}

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

    executePipeline(
        onSuccess = { _: CreateInvitationResult ->
            // Return HTML fragment for HTMX to replace invitations section
            respondHtml(createHTML().div {
                div("notice notice--success") {
                    +"Invitation sent successfully to ${parameters["toUsername"]}"
                }
            })
        },
        onFailure = { failure: CreateInvitationFailures ->
            val errorMessage = when (failure) {
                is CreateInvitationFailures.ValidationError ->
                    "Invalid input: ${failure.errors.joinToString(", ") {
                        when (it) {
                            is ValidationFailure.MissingParameter -> "Missing ${it.parameterName}"
                            is ValidationFailure.InvalidFormat -> "${it.parameterName}: ${it.message ?: "Invalid format"}"
                            else -> it.toString()
                        }
                    }}"
                is CreateInvitationFailures.DatabaseError ->
                    "Unable to send invitation: Database error"
                is CreateInvitationFailures.InsufficientPermissions ->
                    "Permission denied: Admin role required to send invitations"
                is CreateInvitationFailures.UserNotFound ->
                    "User not found: The specified username does not exist"
                is CreateInvitationFailures.DuplicateInvitation ->
                    "Invitation already exists: This user already has a pending invitation"
                is CreateInvitationFailures.CannotInviteSelf ->
                    "Cannot invite yourself to the world"
                is CreateInvitationFailures.NotificationError ->
                    "Invitation created but notification could not be sent"
            }
            respondBadRequest(errorMessage)
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateInvitationInputStep)
            .step(ValidateInviterPermissionsStep(user, worldId))
            .step(ValidateTargetUserStep)
            .step(ValidateNotSelfInviteStep(user.id))
            .step(CreateInvitationStep(user.id, worldId))
            .step(SendInvitationNotificationStep(user.minecraftUsername))
    }
}

// Step 1: Validate input parameters
object ValidateInvitationInputStep : Step<io.ktor.http.Parameters, CreateInvitationFailures, CreateInvitationInput> {
    override suspend fun process(input: io.ktor.http.Parameters): Result<CreateInvitationFailures, CreateInvitationInput> {
        val toUsername = ValidationSteps.required("toUsername", { CreateInvitationFailures.ValidationError(listOf(it)) })
            .process(input)
        val roleString = ValidationSteps.required("role", { CreateInvitationFailures.ValidationError(listOf(it)) })
            .process(input)

        val errors = mutableListOf<ValidationFailure>()
        if (toUsername is Result.Failure) errors.addAll(toUsername.error.errors)
        if (roleString is Result.Failure) errors.addAll(roleString.error.errors)

        // Validate role is Member or Admin (cannot invite as Owner)
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
            Result.failure(CreateInvitationFailures.ValidationError(errors))
        } else {
            Result.success(CreateInvitationInput(toUsername.getOrNull()!!, role!!))
        }
    }
}

// Step 2: Validate inviter has admin+ permissions
class ValidateInviterPermissionsStep(
    private val user: TokenProfile,
    private val worldId: Int
) : Step<CreateInvitationInput, CreateInvitationFailures, CreateInvitationInput> {
    override suspend fun process(input: CreateInvitationInput): Result<CreateInvitationFailures, CreateInvitationInput> {
        // Query to check if user has Admin+ role in world
        return DatabaseSteps.query<CreateInvitationInput, CreateInvitationFailures, Role?>(
            sql = SafeSQL.select("SELECT world_role FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, user.id)
            },
            errorMapper = { CreateInvitationFailures.DatabaseError },
            resultMapper = { rs ->
                if (rs.next()) {
                    val userRole = Role.fromLevel(rs.getInt("world_role"))
                    userRole
                } else {
                    null // User not member of world
                }
            }
        ).process(input).let { result ->
            when (result) {
                is Result.Success -> {
                    val userRole = result.value
                    if (userRole == null || userRole.level > Role.ADMIN.level) {
                        Result.failure(CreateInvitationFailures.InsufficientPermissions)
                    } else {
                        Result.success(input)
                    }
                }
                is Result.Failure -> result
            }
        }
    }
}

// Step 3: Validate target user exists
object ValidateTargetUserStep : Step<CreateInvitationInput, CreateInvitationFailures, Pair<CreateInvitationInput, Int>> {
    override suspend fun process(input: CreateInvitationInput): Result<CreateInvitationFailures, Pair<CreateInvitationInput, Int>> {
        return DatabaseSteps.query<CreateInvitationInput, CreateInvitationFailures, Int?>(
            sql = SafeSQL.select("SELECT u.id FROM users u join minecraft_profiles mp on u.id = mp.user_id WHERE mp.username = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, input.toUsername)
            },
            errorMapper = { CreateInvitationFailures.DatabaseError },
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
                        Result.failure(CreateInvitationFailures.UserNotFound)
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
) : Step<Pair<CreateInvitationInput, Int>, CreateInvitationFailures, Pair<CreateInvitationInput, Int>> {
    override suspend fun process(input: Pair<CreateInvitationInput, Int>): Result<CreateInvitationFailures, Pair<CreateInvitationInput, Int>> {
        val (_, targetUserId) = input
        return if (inviterUserId == targetUserId) {
            Result.failure(CreateInvitationFailures.CannotInviteSelf)
        } else {
            Result.success(input)
        }
    }
}

// Step 5: Create invitation in database
class CreateInvitationStep(
    private val fromUserId: Int,
    private val worldId: Int
) : Step<Pair<CreateInvitationInput, Int>, CreateInvitationFailures, CreateInvitationResult> {
    override suspend fun process(input: Pair<CreateInvitationInput, Int>): Result<CreateInvitationFailures, CreateInvitationResult> {
        val (invitationInput, toUserId) = input

        return DatabaseSteps.update<Pair<CreateInvitationInput, Int>, CreateInvitationFailures>(
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
            },
            errorMapper = { dbFailure ->
                // Handle unique constraint violation for duplicate pending invitations
                if (dbFailure == DatabaseFailure.IntegrityConstraintError) {
                    CreateInvitationFailures.DuplicateInvitation
                } else {
                    CreateInvitationFailures.DatabaseError
                }
            }
        ).process(input).flatMap { invitationId ->
            // Fetch world name for notification
            DatabaseSteps.query<Int, CreateInvitationFailures, String>(
                sql = SafeSQL.select("SELECT name FROM world WHERE id = ?"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, worldId)
                },
                errorMapper = { CreateInvitationFailures.DatabaseError },
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
) : Step<CreateInvitationResult, CreateInvitationFailures, CreateInvitationResult> {
    override suspend fun process(input: CreateInvitationResult): Result<CreateInvitationFailures, CreateInvitationResult> {
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
                is Result.Failure -> {
                    // Log the failure but do not block the invitation creation
                    // The invitation was created successfully, but notification failed
                    Result.failure(CreateInvitationFailures.NotificationError)
                }
            }
        }
    }
}
