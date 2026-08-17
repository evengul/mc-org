package app.mcorg.presentation.handler

import app.mcorg.config.AppConfig
import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.presentation.plugins.MACHINE_SECRET_HEADER
import app.mcorg.presentation.router.configureAppRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import java.sql.SQLTransientConnectionException
import kotlin.test.assertEquals

/**
 * MCO-349 — the health surface.
 *
 * This test used to declare its *own* `/ping` route inside `testApplication` and assert against
 * that, so it passed without the application's route existing at all. It now installs the real
 * `configureAppRouter()`, which means deleting either endpoint from `mainRouter.kt` fails here.
 *
 * The split between the two endpoints is a cost decision, not a style one: Neon suspends the
 * compute after 300s idle, so the endpoint Fly polls every 30s must not touch the database.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PingIT {

    private val secret = "readiness-shared-secret"

    @AfterEach
    fun restoreProvider() {
        // One test below swaps in a failing provider; the extension's real one must come back or
        // every later class in the reused surefire JVM inherits a dead database.
        DatabaseTestExtension.installProvider()
        AppConfig.webhookAdminSecret = null
    }

    /** Readiness is gated by the machine-endpoint secret; liveness deliberately is not. */
    private suspend fun ApplicationTestBuilder.readiness(withSecret: String? = secret) =
        createClient { followRedirects = false }.get("/test/ready") {
            if (withSecret != null) header(MACHINE_SECRET_HEADER, withSecret)
        }

    @Test
    fun `liveness returns OK through the real router`() = testApplication {
        application { configureAppRouter() }

        val response = client.get("/test/ping")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun `liveness is reachable without authentication`() = testApplication {
        // AuthPlugin is installed at the routing root and redirects an unauthenticated caller to
        // sign-in. A platform health check has no cookie, so had /test/ping not been exempt, Fly
        // would have read the 302 as unhealthy and marked down every machine — the check meant to
        // confirm the app is up would have taken it down.
        application { configureAppRouter() }

        val response = createClient { followRedirects = false }.get("/test/ping")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `readiness returns OK when the database answers`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        application { configureAppRouter() }

        val response = readiness()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("READY", response.bodyAsText())
    }

    @Test
    fun `readiness returns 503 when the database is unreachable`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        application { configureAppRouter() }

        // What Hikari raises when it cannot hand over a connection.
        Database.setProvider(object : DatabaseConnectionProvider {
            override fun getConnection(): Connection =
                throw SQLTransientConnectionException("Pool - Connection is not available")

            override fun close() = Unit
        })

        val response = readiness()

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("NOT READY", response.bodyAsText())
    }

    @Test
    fun `readiness refuses a caller with no secret, without touching the database`() = testApplication {
        // The endpoint runs SELECT 1 on the real pool. While it was ungated, a stranger's curl
        // loop could hold Neon's compute awake around the clock — the exact cost failure the
        // liveness/readiness split exists to avoid — and exhaust the pool besides.
        //
        // The failing provider is the assertion that matters: if the gate ever stops
        // short-circuiting, the handler runs, getConnection() throws, and this returns 503 with
        // "NOT READY" instead of a bare 401. So this test distinguishes "refused" from "ran and
        // failed", which a status-code-only check could not.
        AppConfig.webhookAdminSecret = secret
        application { configureAppRouter() }
        Database.setProvider(object : DatabaseConnectionProvider {
            override fun getConnection(): Connection = error("readiness must not reach the database")
            override fun close() = Unit
        })

        val response = readiness(withSecret = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `readiness refuses a caller with the wrong secret`() = testApplication {
        AppConfig.webhookAdminSecret = secret
        application { configureAppRouter() }

        val response = readiness(withSecret = "not-the-secret")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `readiness is inert when no secret is configured`() = testApplication {
        // Fails closed. Production has no WEBHOOK_ADMIN_SECRET today and the probe has no caller
        // yet, so 503-until-configured is the intended resting state rather than a gap.
        AppConfig.webhookAdminSecret = null
        application { configureAppRouter() }

        val response = readiness(withSecret = null)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("This endpoint is not configured", response.bodyAsText())
    }

    @Test
    fun `liveness still answers when the database is unreachable`() = testApplication {
        // The point of the split. A dead pool must not make Fly restart a machine whose JVM is
        // perfectly healthy — during a Neon outage that turns one problem into a restart loop.
        application { configureAppRouter() }

        Database.setProvider(object : DatabaseConnectionProvider {
            override fun getConnection(): Connection =
                throw SQLTransientConnectionException("Pool - Connection is not available")

            override fun close() = Unit
        })

        val response = createClient { followRedirects = false }.get("/test/ping")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
