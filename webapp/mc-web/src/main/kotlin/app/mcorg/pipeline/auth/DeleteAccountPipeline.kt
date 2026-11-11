package app.mcorg.pipeline.auth

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.removeToken
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteAccount() {
    val user = this.getUser()
    val host = this.getHost() ?: "false"

    handlePipeline(
        onSuccess = {
            this.response.cookies.removeToken(host)
            this.clientRedirect("/")
        }
    ) {
        DeleteAccountStep.run(user.id)
        CacheManager.onBanStatusChanged(user.id)
    }
}

/**
 * Deletes the account, and every world it leaves with nobody in it (MCO-662).
 *
 * `world` has no foreign key onto `users`, so `DELETE FROM users` on its own removed only the
 * membership row: a world whose one member deleted their account stayed in the database with no
 * members, unreachable by anyone, while the confirm dialog promised it was deleted. A banned member
 * does not count as somebody left — they cannot open the world either.
 *
 * A world that still has other members is kept for them, as before.
 *
 * One transaction: a failure between the two deletes would otherwise leave the worlds gone and the
 * account still there.
 */
internal val DeleteAccountStep = DatabaseSteps.transaction<Int, Int> { connection ->
    object : Step<Int, AppFailure.DatabaseError, Int> {
        override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
            val worlds = deleteWorldsLeftEmpty(connection).process(input)
            if (worlds is Result.Failure) return worlds
            return deleteUser(connection).process(input)
        }
    }
}

private fun deleteWorldsLeftEmpty(connection: TransactionConnection) = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete(
        """
        DELETE FROM world w
        WHERE EXISTS (SELECT 1 FROM world_members mine WHERE mine.world_id = w.id AND mine.user_id = ?)
          AND NOT EXISTS (
              SELECT 1 FROM world_members other
              WHERE other.world_id = w.id AND other.user_id <> ? AND other.world_role < ?
          )
        """.trimIndent()
    ),
    parameterSetter = { statement, userId ->
        statement.setInt(1, userId)
        statement.setInt(2, userId)
        statement.setInt(3, Role.BANNED.level)
    },
    transactionConnection = connection,
)

private fun deleteUser(connection: TransactionConnection) = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) },
    transactionConnection = connection,
)
