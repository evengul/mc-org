package app.mcorg.api

import app.mcorg.config.AppConfig
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.CF_CONNECTING_IP_HEADER
import app.mcorg.presentation.plugins.EDGE_ORIGIN_HEADER
import app.mcorg.presentation.plugins.SeamRateLimit
import app.mcorg.presentation.plugins.configureEdgeOriginGate
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The limits on the mod's API, end to end through the real routes and the edge gate (MCO-274).
 * The gate is on, so each test can be several clients by sending different `CF-Connecting-IP`s.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ApiRateLimitIT {

    private var savedSecret: String? = null

    @BeforeEach
    fun lockOrigin() {
        savedSecret = AppConfig.edgeOriginSecret
        AppConfig.edgeOriginSecret = EDGE_SECRET
    }

    @AfterEach
    fun restore() {
        AppConfig.edgeOriginSecret = savedSecret
    }

    private fun ApplicationTestBuilder.app() {
        application { configureEdgeOriginGate() }
        routing {
            install(AuthPlugin)
            apiV1Routes()
        }
    }

    private fun HttpRequestBuilder.from(address: String) {
        header(EDGE_ORIGIN_HEADER, EDGE_SECRET)
        header(CF_CONNECTING_IP_HEADER, address)
    }

    private suspend fun ApplicationTestBuilder.createDeviceCode(address: String) =
        client.post("/api/v1/auth/device-code") { from(address) }

    private suspend fun ApplicationTestBuilder.poll(address: String, deviceCode: String) =
        client.post("/api/v1/auth/device-code/poll") {
            from(address)
            contentType(ContentType.Application.Json)
            setBody("""{"device_code":"$deviceCode"}""")
        }

    private suspend fun assertSlowDown(response: HttpResponse) {
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter], "Retry-After")
        val body = response.bodyAsText()
        assertTrue(body.contains("\"error\":\"slow_down\""), body)
    }

    @Test
    fun `one address can create only a burst of device codes, and another is unaffected`() = testApplication {
        app()
        repeat(SeamRateLimit.DEVICE_CODE_CREATE_BURST.limit) {
            assertEquals(HttpStatusCode.OK, createDeviceCode("198.51.100.1").status, "create ${it + 1}")
        }

        assertSlowDown(createDeviceCode("198.51.100.1"))
        assertEquals(HttpStatusCode.OK, createDeviceCode("198.51.100.2").status)
    }

    @Test
    fun `spraying unknown device codes is limited past the per-code interval`() = testApplication {
        app()
        val statuses = (1..SeamRateLimit.DEVICE_CODE_POLL.limit).map {
            poll("198.51.100.3", "unknown-${UUID.randomUUID()}").status
        }
        assertTrue(statuses.all { it == HttpStatusCode.BadRequest }, "expired_token each time, got $statuses")

        assertSlowDown(poll("198.51.100.3", "unknown-${UUID.randomUUID()}"))
    }

    @Test
    fun `invalid bearer tokens spend the API allowance`() = testApplication {
        app()
        val statuses = (1..SeamRateLimit.API.limit).map {
            client.get("/api/v1/worlds") {
                from("198.51.100.4")
                header(HttpHeaders.Authorization, "Bearer not-a-token-$it")
            }.status
        }
        assertTrue(statuses.all { it == HttpStatusCode.Unauthorized }, "got ${statuses.toSet()}")

        assertSlowDown(client.get("/api/v1/worlds") {
            from("198.51.100.4")
            header(HttpHeaders.Authorization, "Bearer not-a-token")
        })
    }

    @Test
    fun `creating a code deletes codes that expired over a day ago, and keeps recent ones`() = testApplication {
        app()
        val stale = "stale-${UUID.randomUUID()}"
        val recent = "recent-${UUID.randomUUID()}"
        insertExpiredCode(stale, expiredAgo = "2 days")
        insertExpiredCode(recent, expiredAgo = "1 hour")

        assertEquals(HttpStatusCode.OK, createDeviceCode("198.51.100.5").status)

        assertEquals(false, deviceCodeExists(stale), "expired two days ago")
        assertEquals(true, deviceCodeExists(recent), "expired an hour ago — /link can still say so")
    }

    private fun insertExpiredCode(deviceCode: String, expiredAgo: String) {
        val userCode = deviceCode.takeLast(8).uppercase()
        DatabaseTestExtension.executeSQL(
            """
            INSERT INTO device_code (device_code, user_code, status, expires_at, interval_seconds)
            VALUES ('$deviceCode', '$userCode', 'pending', now() - interval '$expiredAgo', 5)
            """.trimIndent()
        )
    }

    private fun deviceCodeExists(deviceCode: String): Boolean =
        DriverManager.getConnection(
            DatabaseTestExtension.getJdbcUrl(),
            DatabaseTestExtension.getUsername(),
            DatabaseTestExtension.getPassword(),
        ).use { connection ->
            connection.prepareStatement("SELECT 1 FROM device_code WHERE device_code = ?").use { st ->
                st.setString(1, deviceCode)
                st.executeQuery().use { it.next() }
            }
        }

    private companion object {
        const val EDGE_SECRET = "edge-secret"
    }
}
