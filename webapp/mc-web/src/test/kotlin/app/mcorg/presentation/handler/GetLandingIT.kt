package app.mcorg.presentation.handler

import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetLandingIT : WithUser() {

    @Test
    fun `Redirect to sign-in when not authenticated`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?redirect_to=/", response.headers["Location"])
    }

    @Test
    fun `Redirect to worlds when activeWorldId is null`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }

        val response = client.get("/") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds", response.headers["Location"])
    }

    @Test
    fun `Redirect to world projects when activeWorldId is set`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }

        val response = client.get("/") { addAuthCookie(this, user.copy(activeWorldId = 42)) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds/42/projects", response.headers["Location"])
    }
}
