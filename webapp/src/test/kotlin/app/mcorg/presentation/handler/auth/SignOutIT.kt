package app.mcorg.presentation.handler.auth

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.CreateTokenStep
import app.mcorg.pipeline.auth.CreateUserIfNotExistsStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class SignOutIT {

    lateinit var user: TokenProfile

    @BeforeAll
    fun setup() {
        runBlocking {
            when (val result = CreateUserIfNotExistsStep.process(
                MinecraftProfile(
                    "uuid", "mcname"
                )
            )) {
                is Result.Failure -> fail("Could not create test user: $result")
                is Result.Success -> user = result.value
            }
        }
    }

    @Test
    fun `Sign out removes token`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-out") {
            val jwt = CreateTokenStep.process(user).getOrNull()!!
            cookie(AUTH_COOKIE, jwt)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/", response.headers["Location"])
        val cookie = response.setCookie().find { it.name == AUTH_COOKIE }
        assertEquals("", cookie?.value)
        assertEquals(0, cookie?.maxAge)
    }
}