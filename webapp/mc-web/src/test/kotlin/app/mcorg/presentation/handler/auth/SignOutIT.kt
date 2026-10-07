package app.mcorg.presentation.handler.auth

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.failure.SignOutReason
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
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
        assertEquals("/auth/sign-in", response.headers["Location"])
        val cookie = response.setCookie().find { it.name == AUTH_COOKIE }
        assertEquals("", cookie?.value)
        assertEquals(0, cookie?.maxAge)
    }

    @Test
    fun `An error still signs you out`() = testApplication {
        val client = setup()

        val response = client.get("/auth/sign-out?error=invalid_token") {
            cookie(AUTH_COOKIE, CreateTokenStep.process(user).getOrNull()!!)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTokenCleared(response)
    }

    @Test
    fun `A known code renders its copy, not the code`() = testApplication {
        val client = setup()

        val body = client.get("/auth/sign-out?error=expired_token").bodyAsText()

        assertContains(body, SignOutReason.EXPIRED_TOKEN.heading)
        assertContains(body, SignOutReason.EXPIRED_TOKEN.body)
        assertFalse(body.contains("expired_token"), "the raw code is not shown")
        assertContains(body, "href=\"/auth/sign-in\"")
    }

    @Test
    fun `An unknown code renders the generic page and reflects nothing`() = testApplication {
        val client = setup()

        val response = client.get("/auth/sign-out?error=Your+account+is+suspended")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, SignOutReason.GENERIC.body)
        assertFalse(body.contains("suspended"), "the unknown code is not echoed")
        assertTokenCleared(response)
    }

    @Test
    fun `No other query parameter reaches the page`() = testApplication {
        val client = setup()

        val body = client.get(
            "/auth/sign-out?error=misconfigured&message=Call+0800+PHISH&microsoft_error=Visit+evil.example&claim=sub"
        ).bodyAsText()

        assertContains(body, SignOutReason.MISCONFIGURED.body)
        listOf("message", "0800", "PHISH", "microsoft_error", "evil.example", "claim").forEach {
            assertFalse(body.contains(it), "'$it' is not reflected")
        }
    }

    @Test
    fun `A malformed token ends on the sign-out page, not in a redirect loop`() = testApplication {
        val client = setup()
        var cookie: String? = "not-a-jwt"
        var path = "/some-protected-path"
        val visited = mutableListOf<String>()

        // Follow redirects by hand, carrying the cookie the way a browser would, so a loop shows up
        // as a path visited twice instead of a client giving up after its own hop limit.
        repeat(5) {
            visited += path
            val response = client.get(path) { cookie?.let { cookie(AUTH_COOKIE, it) } }
            response.setCookie().find { it.name == AUTH_COOKIE }?.let { cookie = it.value.ifEmpty { null } }
            if (response.status != HttpStatusCode.Found) {
                assertEquals(HttpStatusCode.OK, response.status)
                assertContains(response.bodyAsText(), SignOutReason.INVALID_TOKEN.heading)
                assertEquals(null, cookie, "the bad token is gone by the time the page renders")
                return@testApplication
            }
            path = response.headers["Location"]!!
        }
        fail("Still redirecting after five hops: $visited")
    }

    private fun ApplicationTestBuilder.setup() = createClient { followRedirects = false }.also {
        routing {
            install(AuthPlugin)
            route("/auth") {
                authRouter()
            }
            get("/some-protected-path") {
                call.respond(HttpStatusCode.OK, "protected content")
            }
        }
    }

    private fun assertTokenCleared(response: HttpResponse) {
        val cookie = response.setCookie().find { it.name == AUTH_COOKIE }
        assertEquals("", cookie?.value)
        assertEquals(0, cookie?.maxAge)
    }
}
