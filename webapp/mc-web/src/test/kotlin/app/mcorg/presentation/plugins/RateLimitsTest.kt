package app.mcorg.presentation.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RateLimitsTest {

    @Nested
    inner class Windows {

        private var now = 1_000_000L
        private val windows = FixedWindows { now }

        @Test
        fun `the call past the limit waits until the window reopens`() {
            val limit = SeamRateLimit.DEVICE_CODE_CREATE_BURST
            repeat(limit.limit) { assertNull(windows.tryAcquire(limit, "a"), "call ${it + 1}") }

            now += 15_000
            assertEquals(45L, windows.tryAcquire(limit, "a"))
        }

        @Test
        fun `a window reopens in full once its period has passed`() {
            val limit = SeamRateLimit.DEVICE_CODE_CREATE_BURST
            repeat(limit.limit + 3) { windows.tryAcquire(limit, "a") }

            now += limit.period.inWholeMilliseconds
            repeat(limit.limit) { assertNull(windows.tryAcquire(limit, "a"), "call ${it + 1} after reopening") }
        }

        @Test
        fun `clients and limits are counted apart`() {
            val limit = SeamRateLimit.DEVICE_CODE_CREATE_BURST
            repeat(limit.limit + 1) { windows.tryAcquire(limit, "a") }

            assertNull(windows.tryAcquire(limit, "b"), "another client")
            assertNull(windows.tryAcquire(SeamRateLimit.API, "a"), "another limit")
        }

        @Test
        fun `a sweep drops closed windows and keeps open ones`() {
            windows.tryAcquire(SeamRateLimit.DEVICE_CODE_CREATE_BURST, "a")
            windows.tryAcquire(SeamRateLimit.DEVICE_CODE_CREATE_SUSTAINED, "a")

            now += SeamRateLimit.DEVICE_CODE_CREATE_BURST.period.inWholeMilliseconds
            windows.sweep()

            assertEquals(1, windows.size, "only the hour-long window is still open")
        }
    }

    @Nested
    inner class Routing {

        private val refuseEveryone = createRouteScopedPlugin("RefuseEveryone") {
            onCall { call -> call.respond(HttpStatusCode.Unauthorized) }
        }

        @Test
        fun `a request a route plugin refuses still spends the bucket`() = testApplication {
            // The reason this is not Ktor's RateLimit: there, ApiBearerAuthPlugin's 401 for a bad
            // token answers first and the bucket is never touched.
            routing {
                rateLimited(SeamRateLimit.SIGN_IN) {
                    route("/refused") {
                        install(refuseEveryone)
                        get { call.respondText("unreachable") }
                    }
                }
            }
            val statuses = callUntilPastLimit(SeamRateLimit.SIGN_IN, "/refused")
            assertEquals(HttpStatusCode.TooManyRequests, statuses.last(), "got $statuses")
        }

        @Test
        fun `sibling limits do not leak into each other`() = testApplication {
            routing {
                rateLimited(SeamRateLimit.DEVICE_CODE_CREATE_BURST) { get("/create") { call.respondText("ok") } }
                rateLimited(SeamRateLimit.SIGN_IN) { get("/sign-in") { call.respondText("ok") } }
            }
            repeat(SeamRateLimit.DEVICE_CODE_CREATE_BURST.limit + 1) { client.get("/sign-in") }

            assertEquals(HttpStatusCode.OK, client.get("/create").status)
        }

        @Test
        fun `a nested limit and its parent's both apply`() = testApplication {
            routing {
                rateLimited(SeamRateLimit.API) {
                    rateLimited(SeamRateLimit.DEVICE_CODE_CREATE_BURST) { get("/inner") { call.respondText("ok") } }
                    get("/outer") { call.respondText("ok") }
                }
            }
            val inner = callUntilPastLimit(SeamRateLimit.DEVICE_CODE_CREATE_BURST, "/inner")
            assertEquals(HttpStatusCode.TooManyRequests, inner.last(), "the inner limit, got $inner")

            // The inner calls, refused ones included, also spent the parent's allowance.
            val remaining = SeamRateLimit.API.limit - inner.size
            val outer = (1..remaining + 1).map { client.get("/outer").status }
            assertEquals(HttpStatusCode.TooManyRequests, outer.last(), "the outer limit, got $outer")
            assertEquals(HttpStatusCode.OK, outer[remaining - 1])
        }
    }

    @Nested
    inner class Responses {

        private fun ApplicationTestBuilder.limitedRoutes() = routing {
            rateLimited(SeamRateLimit.DEVICE_CODE_CREATE_BURST) {
                get("/api/v1/limited") { call.respondText("ok") }
                get("/limited") { call.respondText("ok") }
            }
        }

        private suspend fun ApplicationTestBuilder.exhaust(path: String, htmx: Boolean = false): HttpResponse {
            repeat(SeamRateLimit.DEVICE_CODE_CREATE_BURST.limit) { client.get(path) }
            return client.get(path) { if (htmx) header("HX-Request", "true") }
        }

        @Test
        fun `the API answers slow_down with Retry-After`() = testApplication {
            limitedRoutes()
            val response = exhaust("/api/v1/limited")

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            assertEquals("60", response.headers[HttpHeaders.RetryAfter])
            assertTrue(response.bodyAsText().contains("\"error\":\"slow_down\""), response.bodyAsText())
        }

        @Test
        fun `a page load gets the status page`() = testApplication {
            limitedRoutes()
            val response = exhaust("/limited")

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("<html"), "a whole page")
            assertTrue(body.contains("429"), body)
        }

        @Test
        fun `an HTMX request gets an alert`() = testApplication {
            limitedRoutes()
            val response = exhaust("/limited", htmx = true)

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("rate-limited-error"), body)
            assertTrue(!body.contains("<html"), "a fragment, not a page")
        }
    }

    private suspend fun ApplicationTestBuilder.callUntilPastLimit(limit: SeamRateLimit, path: String) =
        (1..limit.limit + 1).map { client.get(path).status }
}
