package app.mcorg.presentation.handler

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.presentation.router.configureAppRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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

    @AfterEach
    fun restoreProvider() {
        // One test below swaps in a failing provider; the extension's real one must come back or
        // every later class in the reused surefire JVM inherits a dead database.
        DatabaseTestExtension.installProvider()
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
        application { configureAppRouter() }

        val response = createClient { followRedirects = false }.get("/test/ready")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("READY", response.bodyAsText())
    }

    @Test
    fun `readiness returns 503 when the database is unreachable`() = testApplication {
        application { configureAppRouter() }

        // What Hikari raises when it cannot hand over a connection.
        Database.setProvider(object : DatabaseConnectionProvider {
            override fun getConnection(): Connection =
                throw SQLTransientConnectionException("Pool - Connection is not available")

            override fun close() = Unit
        })

        val response = createClient { followRedirects = false }.get("/test/ready")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("NOT READY", response.bodyAsText())
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
