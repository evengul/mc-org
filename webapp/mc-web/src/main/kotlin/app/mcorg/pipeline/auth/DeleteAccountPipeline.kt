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
        val deletedWorlds = DeleteAccountStep.run(user.id)
        CacheManager.onBanStatusChanged(user.id)
        // After the commit, as DeleteWorldPipeline does: a cached "this world exists" or "is a
        // member" would otherwise outlive the world by up to the cache's TTL.
        deletedWorlds.forEach { CacheManager.onWorldDeleted(it) }
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
 * One transaction: a failure between the deletes would otherwise leave the worlds gone and the
 * account still there. The user's worlds are locked first, so two last members deleting their
 * accounts at the same moment are serialised: under READ COMMITTED each would otherwise still see
 * the other's membership, keep the world, and leave it with nobody in it.
 *
 * Returns the ids of the deleted worlds, for the cache.
 */
internal val DeleteAccountStep = DatabaseSteps.transaction<Int, List<Int>> { connection ->
    object : Step<Int, AppFailure.DatabaseError, List<Int>> {
        override suspend fun process(input: Int): Result<AppFailure.DatabaseError, List<Int>> {
            val locked = lockOwnWorlds(connection).process(input)
            if (locked is Result.Failure) return locked
            val worlds = deleteWorldsLeftEmpty(connection).process(input)
            if (worlds is Result.Failure) return worlds
            val user = deleteUser(connection).process(input)
            if (user is Result.Failure) return user
            return worlds
        }
    }
}

private fun idList(rs: java.sql.ResultSet): List<Int> = buildList { while (rs.next()) add(rs.getInt(1)) }

private fun lockOwnWorlds(connection: TransactionConnection) = DatabaseSteps.query<Int, List<Int>>(
    sql = SafeSQL.select(
        """
        SELECT w.id FROM world w
        JOIN world_members m ON m.world_id = w.id
        WHERE m.user_id = ?
        ORDER BY w.id
        FOR UPDATE OF w
        """.trimIndent()
    ),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) },
    resultMapper = ::idList,
    transactionConnection = connection,
)

private fun deleteWorldsLeftEmpty(connection: TransactionConnection) = DatabaseSteps.query<Int, List<Int>>(
    sql = SafeSQL.delete(
        """
        DELETE FROM world w
        WHERE EXISTS (SELECT 1 FROM world_members mine WHERE mine.world_id = w.id AND mine.user_id = ?)
          AND NOT EXISTS (
              SELECT 1 FROM world_members other
              WHERE other.world_id = w.id AND other.user_id <> ? AND other.world_role < ?
          )
        RETURNING w.id
        """.trimIndent()
    ),
    parameterSetter = { statement, userId ->
        statement.setInt(1, userId)
        statement.setInt(2, userId)
        statement.setInt(3, Role.BANNED.level)
    },
    resultMapper = ::idList,
    transactionConnection = connection,
)

private fun deleteUser(connection: TransactionConnection) = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) },
    transactionConnection = connection,
)
