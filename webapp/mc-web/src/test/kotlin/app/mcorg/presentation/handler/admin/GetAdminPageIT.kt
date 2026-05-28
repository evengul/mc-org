package app.mcorg.presentation.handler.admin

import app.mcorg.presentation.handler.AdminHandler
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetAdminPageIT : WithUser() {

    @Test
    fun `Superadmin can render admin page`() = testApplication {
        val client = setup()
        val admin = createExtraUser("superadmin")

        val response = client.get("/admin") {
            addAuthCookie(this, admin)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Admin Dashboard")
        assertContains(body, "USER MANAGEMENT")
        assertContains(body, "WORLD MANAGEMENT")
        assertContains(body, "data-table")
        assertContains(body, "pagination-info-users")
        assertContains(body, "pagination-info-worlds")
    }

    @Test
    fun `Non-admin user gets 404 from admin page`() = testApplication {
        val client = setup()
        val plain = createExtraUser()

        val response = client.get("/admin") {
            addAuthCookie(this, plain)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `Unauthenticated request to admin redirects to sign-in`() = testApplication {
        val client = setup()

        val response = client.get("/admin")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            with(AdminHandler()) {
                adminRoutes()
            }
        }
        return createClient { followRedirects = false }
    }
}
