package app.mcorg.presentation.handler.profile

import app.mcorg.presentation.handler.ProfileHandler
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetProfileIT : WithUser() {

    @Test
    fun `Get profile page`() = testApplication {
        val client = setup()

        val response = client.get("/app/profile") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), user.minecraftUsername)
    }

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            route("/app") {
                with(ProfileHandler()) {
                    profileRoutes()
                }
            }
        }
        return createClient {
            followRedirects = false
        }
    }
}