package app.mcorg.presentation.handler

import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.router.appRouterV2
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class NotificationsRouteRemovedIT : WithUser() {

    @Test
    fun `GET notifications returns 404 after notifications UI removal`() = testApplication {
        val client = setup()

        val response = client.get("/notifications") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            appRouterV2()
        }
        return createClient { followRedirects = false }
    }
}
