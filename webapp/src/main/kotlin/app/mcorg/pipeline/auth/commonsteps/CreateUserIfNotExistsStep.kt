package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.ResultSet

data object CreateUserIfNotExistsStep : Step<MinecraftProfile, AppFailure.DatabaseError, TokenProfile> {

    val logger: Logger = LoggerFactory.getLogger(CreateUserIfNotExistsStep::class.java)

    override suspend fun process(input: MinecraftProfile): Result<AppFailure.DatabaseError, TokenProfile> {
        logger.info("Processing user creation for UUID: ${input.uuid}, username: ${input.username}")

        // First, check if a user with this UUID already exists
        val checkUserStep = DatabaseSteps.query(
            sql = SafeSQL.select("""
                SELECT u.id, u.email, mp.uuid, mp.username 
                FROM users u 
                JOIN minecraft_profiles mp ON u.id = mp.user_id 
                WHERE mp.uuid = ?
            """.trimIndent()),
            parameterSetter = { statement, profile: MinecraftProfile ->
                statement.setString(1, profile.uuid)
            },
            resultMapper = { resultSet: ResultSet ->
                if (resultSet.next()) {
                    ExistingUser(
                        id = resultSet.getInt("id"),
                        email = resultSet.getString("email"),
                        uuid = resultSet.getString("uuid"),
                        currentUsername = resultSet.getString("username")
                    )
                } else {
                    null
                }
            }
        )

        return when (val userResult = checkUserStep.process(input)) {
            is Result.Failure -> userResult
            is Result.Success -> {
                val existingUser = userResult.value
                if (existingUser != null) {
                    // User exists, check if username needs to be updated
                    if (existingUser.currentUsername != input.username) {
                        logger.info("Updating username for UUID ${input.uuid} from '${existingUser.currentUsername}' to '${input.username}'")
                        updateUsernameAndReturnProfile(existingUser, input.username)
                    } else {
                        // User exists and username matches, fetch roles and return existing profile
                        when (val rolesResult = fetchUserRoles(existingUser.id)) {
                            is Result.Failure -> rolesResult
                            is Result.Success -> Result.success(TokenProfile(
                                id = existingUser.id,
                                uuid = existingUser.uuid,
                                minecraftUsername = existingUser.currentUsername,
                                displayName = existingUser.currentUsername,
                                roles = rolesResult.value
                            ))
                        }
                    }
                } else {
                    // User doesn't exist, create new user
                    logger.info("Creating new user for UUID: ${input.uuid}")
                    createNewUserAndProfile(input)
                }
            }
        }
    }

    private suspend fun fetchUserRoles(userId: Int): Result<AppFailure.DatabaseError, List<String>> {
        val fetchRolesStep = DatabaseSteps.query(
            sql = SafeSQL.select("""
                SELECT role
                FROM global_user_roles
                WHERE user_id = ? AND is_active = TRUE
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """.trimIndent()),
            parameterSetter = { statement, _: Int ->
                statement.setInt(1, userId)
            },
            resultMapper = { resultSet: ResultSet ->
                val roles = mutableListOf<String>()
                while (resultSet.next()) {
                    roles.add(resultSet.getString("role"))
                }
                roles.toList()
            }
        )

        return fetchRolesStep.process(userId)
    }

    private suspend fun updateUsernameAndReturnProfile(
        existingUser: ExistingUser,
        newUsername: String
    ): Result<AppFailure.DatabaseError, TokenProfile> {
        // First update the minecraft profile username
        val updateUsernameStep = DatabaseSteps.update(
            sql = SafeSQL.update("""
                UPDATE minecraft_profiles 
                SET username = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE uuid = ?
            """.trimIndent()),
            parameterSetter = { statement, _: Unit ->
                statement.setString(1, newUsername)
                statement.setString(2, existingUser.uuid)
            }
        )

        return when (val updateResult = updateUsernameStep.process(Unit)) {
            is Result.Failure -> updateResult
            is Result.Success -> {
                if (updateResult.value > 0) {
                    // Also update world_members where display_name matches the old username
                    updateWorldMemberDisplayNames(existingUser.id, existingUser.currentUsername, newUsername)

                    // Fetch roles for the updated profile
                    when (val rolesResult = fetchUserRoles(existingUser.id)) {
                        is Result.Failure -> rolesResult
                        is Result.Success -> Result.success(TokenProfile(
                            id = existingUser.id,
                            uuid = existingUser.uuid,
                            minecraftUsername = newUsername,
                            displayName = newUsername,
                            roles = rolesResult.value
                        ))
                    }
                } else {
                    logger.error("Failed to update username for UUID: ${existingUser.uuid}")
                    Result.failure(AppFailure.DatabaseError.UnknownError)
                }
            }
        }
    }

    private suspend fun updateWorldMemberDisplayNames(
        userId: Int,
        oldUsername: String,
        newUsername: String
    ) {
        val updateWorldMembersStep = DatabaseSteps.update(
            sql = SafeSQL.update("""
                UPDATE world_members 
                SET display_name = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE user_id = ? AND display_name = ?
            """.trimIndent()),
            parameterSetter = { statement, _: Unit ->
                statement.setString(1, newUsername)
                statement.setInt(2, userId)
                statement.setString(3, oldUsername)
            }
        )

        when (val result = updateWorldMembersStep.process(Unit)) {
            is Result.Success -> {
                if (result.value > 0) {
                    logger.info("Updated ${result.value} world member display names for user $userId from '$oldUsername' to '$newUsername'")
                } else {
                    logger.debug("No world member display names needed updating for user $userId")
                }
            }
            is Result.Failure -> {
                logger.warn("Failed to update world member display names for user $userId: ${result.error}")
                // Don't fail the entire operation if world member updates fail, just log the warning
            }
        }
    }

    private suspend fun createNewUserAndProfile(profile: MinecraftProfile): Result<AppFailure.DatabaseError, TokenProfile> {
        // We need to create both user and minecraft_profile in a transaction
        val createUserTransaction = DatabaseSteps.transaction(
            step = { CreateUserWithProfileStep(it) }
        )

        return createUserTransaction.process(profile)
    }

    private data class ExistingUser(
        val id: Int,
        val email: String,
        val uuid: String,
        val currentUsername: String
    )

    private data class CreateUserWithProfileStep(val connection: TransactionConnection) : Step<MinecraftProfile, AppFailure.DatabaseError, TokenProfile> {
        override suspend fun process(input: MinecraftProfile): Result<AppFailure.DatabaseError, TokenProfile> {
            // First create the user record with a placeholder email
            val createUserStep = DatabaseSteps.query(
                sql = SafeSQL.insert("""
                    INSERT INTO users (email) 
                    VALUES (?) 
                    RETURNING id
                """.trimIndent()),
                parameterSetter = { statement, profile: MinecraftProfile ->
                    // Use UUID as temporary email since we don't have email in MinecraftProfile
                    statement.setString(1, "${profile.uuid}@minecraft.temp")
                },
                resultMapper = { resultSet: ResultSet ->
                    if (resultSet.next()) {
                        resultSet.getInt("id")
                    } else {
                        throw RuntimeException("Failed to get user ID after insert")
                    }
                },
                connection
            )

            return when (val userResult = createUserStep.process(input)) {
                is Result.Failure -> userResult
                is Result.Success -> {
                    val userId = userResult.value

                    // Now create the minecraft profile
                    val createProfileStep = DatabaseSteps.update(
                        sql = SafeSQL.insert("""
                            INSERT INTO minecraft_profiles (user_id, uuid, username) 
                            VALUES (?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, profile: MinecraftProfile ->
                            statement.setInt(1, userId)
                            statement.setString(2, profile.uuid)
                            statement.setString(3, profile.username)
                        },
                        connection
                    )

                    val addDemoRoleStep = DatabaseSteps.update(
                        sql = SafeSQL.insert("""
                                        INSERT INTO global_user_roles (user_id, role, is_active, created_at, updated_at) 
                                        VALUES (?, 'demo_user', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                    """.trimIndent()),
                        parameterSetter = { statement, _: Unit ->
                            statement.setInt(1, userId)
                        },
                        connection
                    )

                    when (val profileResult = createProfileStep.process(input)) {
                        is Result.Failure -> profileResult
                        is Result.Success -> {
                            if (profileResult.value > 0) {
                                if (input.isDemoUser) {
                                    when (val demoRoleResult = addDemoRoleStep.process(Unit)) {
                                        is Result.Failure -> return demoRoleResult
                                        is Result.Success -> {
                                            if (demoRoleResult.value > 0) {
                                                Result.success(TokenProfile(
                                                    id = userId,
                                                    uuid = input.uuid,
                                                    minecraftUsername = input.username,
                                                    displayName = input.username,
                                                    roles = listOf("demo_user")
                                                ))
                                            } else {
                                                Result.failure(AppFailure.DatabaseError.UnknownError)
                                            }
                                        }
                                    }
                                } else {
                                    Result.success(TokenProfile(
                                        id = userId,
                                        uuid = input.uuid,
                                        minecraftUsername = input.username,
                                        displayName = input.username,
                                        roles = emptyList() // New users have no roles initially
                                    ))
                                }
                            } else {
                                Result.failure(AppFailure.DatabaseError.UnknownError)
                            }
                        }
                    }
                }
            }
        }
    }
}
