package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Test as TestEnv
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals

class PreviewGateIT {

    @AfterEach
    fun reset() {
        AppConfig.env = Local
        AppConfig.previewPassword = null
    }

    private fun basic(user: String, pass: String) =
        "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

    private fun ApplicationTestBuilder.gatedApp() {
        application { configurePreviewGate() }
        routing {
            get("/x") { call.respondText("ok") }
            get("/test/ping") { call.respondText("OK") }
        }
    }

    @Test
    fun `TEST without credentials returns 401 with a Basic challenge`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/x")

        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertEquals("Basic realm=\"Seam Preview\", charset=\"UTF-8\"", res.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun `TEST with the correct username and password passes through`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/x") { header(HttpHeaders.Authorization, basic("admin", "s3cret")) }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("ok", res.bodyAsText())
    }

    @Test
    fun `TEST with a wrong password returns 401`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/x") { header(HttpHeaders.Authorization, basic("admin", "nope")) }

        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `TEST with a wrong username returns 401`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/x") { header(HttpHeaders.Authorization, basic("root", "s3cret")) }

        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `TEST fails closed with 503 when the password is unset`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = null
        gatedApp()

        val res = client.get("/x") { header(HttpHeaders.Authorization, basic("admin", "whatever")) }

        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
    }

    @Test
    fun `LOCAL is never gated`() = testApplication {
        AppConfig.env = Local
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/x")

        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `health probe path is reachable without credentials in TEST`() = testApplication {
        AppConfig.env = TestEnv
        AppConfig.previewPassword = "s3cret"
        gatedApp()

        val res = client.get("/test/ping")

        assertEquals(HttpStatusCode.OK, res.status)
    }
}
