package app.mcorg.pipeline.auth

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.failure.CreateUserIfNotExistsFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data object CreateUserIfNotExistsStep : Step<MinecraftProfile, CreateUserIfNotExistsFailure, User> {

    val logger: Logger = LoggerFactory.getLogger(CreateUserIfNotExistsStep::class.java)

    override suspend fun process(input: MinecraftProfile): Result<CreateUserIfNotExistsFailure, User> {
        return useConnection({ CreateUserIfNotExistsFailure.Other(it) }) {
            prepareStatement("select id, username, minecraft_uuid from users where minecraft_uuid = ?")
                .apply { setString(1, input.uuid) }
                .executeQuery()
                .apply {
                    if (next()) {
                        val userId = getInt("id")
                        val username = getString("username")
                        // If the username on this UUID does not match, it has changed in the game.
                        // This is a rare case, but we should update the username in the database.
                        if (username != input.username) {
                            logger.info("Updating username for user ID = $userId / uuid = ${input.uuid} from '$username' to '${input.username}'")
                            prepareStatement("update users set username = ?, updated_by = ?, updated_at = now() where id = ?")
                                .apply {
                                    setString(1, input.username)
                                    setString(2, input.username)
                                    setInt(3, userId)
                                }
                                .executeUpdate()
                        }
                        return@useConnection Result.success(
                            User(
                                id = userId,
                                username = input.username,
                            )
                        )
                    }
                }

            val id = prepareStatement("insert into users(username, display_name, minecraft_uuid, email, created_by, updated_by) values (?, ?, ?, ?, ?, ?) returning id")
                .apply {
                    setString(1, input.username)
                    setString(2, input.username)
                    setString(3, input.uuid)
                    setString(4, input.email)
                    setString(5, input.username)
                    setString(6, input.username)
                }
                .getReturnedId(CreateUserIfNotExistsFailure.Other(DatabaseFailure.NoIdReturned))

            return@useConnection runBlocking {
                id.map { User(it, input.username) }
            }
        }
    }
}
