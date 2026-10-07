package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EdgeOriginGateTest {

    private var savedSecret: String? = null

    @BeforeEach
    fun save() {
        savedSecret = AppConfig.edgeOriginSecret
    }

    @AfterEach
    fun restore() {
        AppConfig.edgeOriginSecret = savedSecret
    }

    private fun ApplicationTestBuilder.gatedApp() {
        application { configureEdgeOriginGate() }
        routing {
            get("/test/ping") { call.respondText("OK") }
            get("/whoami") { call.respondText(call.clientAddress()) }
        }
    }

    @Nested
    inner class Locked {

        @BeforeEach
        fun lock() {
            AppConfig.edgeOriginSecret = "edge-secret"
        }

        @Test
        fun `a request that skipped the edge is refused`() = testApplication {
            gatedApp()
            assertEquals(HttpStatusCode.Forbidden, client.get("/whoami").status)
        }

        @Test
        fun `a wrong secret is refused`() = testApplication {
            gatedApp()
            assertEquals(HttpStatusCode.Forbidden, client.get("/whoami") { header(EDGE_ORIGIN_HEADER, "guess") }.status)
        }

        @Test
        fun `Fly's health check gets through without the secret`() = testApplication {
            gatedApp()
            assertEquals(HttpStatusCode.OK, client.get("/test/ping").status)
        }

        @Test
        fun `a request from the edge is keyed on the address Cloudflare saw`() = testApplication {
            gatedApp()
            val response = client.get("/whoami") {
                header(EDGE_ORIGIN_HEADER, "edge-secret")
                header(CF_CONNECTING_IP_HEADER, "203.0.113.7")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("203.0.113.7", response.bodyAsText())
        }
    }

    @Nested
    inner class Open {

        @BeforeEach
        fun open() {
            AppConfig.edgeOriginSecret = null
        }

        @Test
        fun `without a secret nothing is refused`() = testApplication {
            gatedApp()
            assertEquals(HttpStatusCode.OK, client.get("/whoami").status)
        }

        @Test
        fun `without a secret a forged CF-Connecting-IP is ignored`() = testApplication {
            gatedApp()
            val response = client.get("/whoami") { header(CF_CONNECTING_IP_HEADER, "203.0.113.7") }
            assertNotEquals("203.0.113.7", response.bodyAsText())
        }
    }

    @Nested
    inner class Keys {

        @Test
        fun `an IPv4 address is its own key`() {
            assertEquals("203.0.113.7", rateLimitKeyFor("203.0.113.7"))
        }

        @Test
        fun `IPv6 addresses in one 64 share a key`() {
            val a = rateLimitKeyFor("2001:db8:1234:5678::1")
            val b = rateLimitKeyFor("2001:db8:1234:5678:ffff:eeee:dddd:cccc")
            assertEquals(a, b)
            assertEquals("2001:db8:1234:5678:0:0:0:0/64", a)
        }

        @Test
        fun `IPv6 addresses in different 64s do not`() {
            assertNotEquals(rateLimitKeyFor("2001:db8:1234:5678::1"), rateLimitKeyFor("2001:db8:1234:5679::1"))
        }

        @Test
        fun `an IPv4-mapped address is keyed as the IPv4 address`() {
            assertEquals("203.0.113.7", rateLimitKeyFor("::ffff:203.0.113.7"))
        }

        @Test
        fun `a value that is not an address is kept as-is, never resolved`() {
            assertEquals("localhost", rateLimitKeyFor("localhost"))
            assertEquals("example.invalid", rateLimitKeyFor("example.invalid"))
        }
    }
}
