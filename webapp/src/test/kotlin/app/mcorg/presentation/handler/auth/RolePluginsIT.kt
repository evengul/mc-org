package app.mcorg.presentation.handler.auth

import app.mcorg.presentation.plugins.AdminPlugin
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.BannedPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class RolePluginsIT : WithUser() {

    @Test
    fun `A banned user should get 403 on protected routes`() = testApplication {
        val client = setup()

        val user = createExtraUser("banned")

        val response = client.get("/app") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `A non-banned user should access protected routes`() = testApplication {
        val client = setup()

        val user = createExtraUser()

        val response = client.get("/app") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("App content", response.bodyAsText())
    }

    @Test
    fun `A user with no roles should not access adminRoutes`() = testApplication {
        val client = setup()

        val user = createExtraUser()

        val response = client.get("/app/admin") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `An admin user should access adminRoutes`() = testApplication {
        val client = setup()

        val user = createExtraUser("superadmin")

        val response = client.get("/app/admin") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Admin content", response.bodyAsText())
    }

    // TODO: WorldAdmin role tests. Do when world routes tests are implemented.

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            install(BannedPlugin)
            route("/app") {
                get {
                    call.respondText("App content")
                }
                route("/admin") {
                    install(AdminPlugin)
                    get {
                        call.respondText("Admin content")
                    }
                }
            }
        }

        return createClient { followRedirects = false }
    }
}