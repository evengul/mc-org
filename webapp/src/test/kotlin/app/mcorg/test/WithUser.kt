package app.mcorg.test

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.fail
import java.util.UUID
import kotlin.random.Random

open class WithUser {
    protected val user: TokenProfile by lazy {
        runBlocking {
            when (val result = CreateUserIfNotExistsStep.process(
                MinecraftProfile(
                    "uuid", "mcname"
                )
            )) {
                is Result.Failure -> fail("Could not create test user: $result")
                is Result.Success -> result.value
            }
        }
    }

    protected fun createExtraUser(vararg roles: String) = runBlocking {
        val createdUser = when (val result = CreateUserIfNotExistsStep.process(
            MinecraftProfile(
                UUID.randomUUID().toString(),
                "mcname${Random.nextInt(1000, 9999)}"
            )
        )) {
            is Result.Failure -> fail("Could not create extra test user: $result")
            is Result.Success -> result.value
        }

        roles.forEach { addRole(createdUser.id, it) }

        return@runBlocking createdUser.copy(roles = roles.toList())
    }

    protected fun addAuthCookie(httpRequestBuilder: HttpRequestBuilder, user: TokenProfile = this.user) {
        val jwt = runBlocking { CreateTokenStep.process(user).getOrNull()!! }
        httpRequestBuilder.cookie(AUTH_COOKIE, jwt)
    }

    protected fun addRole(userId: Int, role: String) = runBlocking {
        val sql = SafeSQL.insert(
            """
                INSERT INTO global_user_roles (user_id, role) VALUES (?, ?)
            """.trimIndent()
        )
        DatabaseSteps.update<Unit, DatabaseFailure>(
            sql = sql,
            parameterSetter = { statement, _ -> statement.setInt(1, userId); statement.setString(2, role) },
            errorMapper = { it }
        ).process(Unit)
    }
}