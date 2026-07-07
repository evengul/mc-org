package app.mcorg.api

import app.mcorg.presentation.handler.link.handleApproveLinkPage
import app.mcorg.presentation.handler.link.handleGetLinkPage
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ApiAuthIT : WithUser() {

    private suspend fun ApplicationTestBuilder.createDeviceCode(): DeviceCodeResponse {
        val response = client.post("/api/v1/auth/device-code")
        assertEquals(HttpStatusCode.OK, response.status)
        return apiJson.decodeFromString(DeviceCodeResponse.serializer(), response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.poll(deviceCode: String): HttpResponse =
        client.post("/api/v1/auth/device-code/poll") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_code":"$deviceCode"}""")
        }

    @Test
    fun `device-code endpoint returns a code, user code and verification uri`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val dc = createDeviceCode()
        assertTrue(dc.deviceCode.isNotBlank())
        assertTrue(dc.userCode.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")))
        assertTrue(dc.verificationUri.endsWith("/link"))
        assertEquals(600L, dc.expiresIn)
        assertEquals(5, dc.interval)
    }

    @Test
    fun `polling a pending code returns authorization_pending`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val dc = createDeviceCode()
        val response = poll(dc.deviceCode)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = apiJson.decodeFromString(PollPendingResponse.serializer(), response.bodyAsText())
        assertEquals("authorization_pending", body.error)
    }

    @Test
    fun `polling an unknown code returns expired_token`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val response = poll("not-a-real-device-code")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("expired_token", apiJson.decodeFromString(PollPendingResponse.serializer(), response.bodyAsText()).error)
    }

    @Test
    fun `polling too fast returns slow_down`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val dc = createDeviceCode()
        poll(dc.deviceCode) // first poll stamps last_polled_at
        val second = poll(dc.deviceCode)
        assertEquals(HttpStatusCode.BadRequest, second.status)
        assertEquals("slow_down", apiJson.decodeFromString(PollPendingResponse.serializer(), second.bodyAsText()).error)
    }

    @Test
    fun `approving via link page then polling mints a token exactly once`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val dc = createDeviceCode()

        // Approve the code as the signed-in user through the browser /link page.
        val approve = client.post("/link") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("user_code" to dc.userCode).formUrlEncode())
        }
        assertEquals(HttpStatusCode.OK, approve.status)
        assertTrue(approve.bodyAsText().contains("Device linked"))

        // First poll after approval mints the token.
        val first = poll(dc.deviceCode)
        assertEquals(HttpStatusCode.OK, first.status)
        val success = apiJson.decodeFromString(PollSuccessResponse.serializer(), first.bodyAsText())
        assertTrue(success.accessToken.isNotBlank())
        assertEquals("Bearer", success.tokenType)
        assertEquals("mcname", success.username)

        // The token works against a bearer endpoint.
        val worlds = client.get("/api/v1/worlds") { header("Authorization", "Bearer ${success.accessToken}") }
        assertEquals(HttpStatusCode.OK, worlds.status)

        // The device code is spent: its one-time token issuance can never be claimed again, so a
        // later poll (past the slow_down window) would return expired_token rather than a 2nd token.
        val reclaim = runCatchingBlocking { ClaimDeviceCodeTokenStep.process(dc.deviceCode) }
        assertEquals(0, (reclaim as app.mcorg.pipeline.Result.Success).value)
    }

    @Test
    fun `link page renders the code entry form for a signed-in user`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val response = client.get("/link") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Link a device"))
        assertTrue(body.contains("name=\"user_code\""))
    }

    @Test
    fun `approving an unknown code shows an error`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val response = client.post("/link") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("user_code" to "ZZZZ-ZZZZ").formUrlEncode())
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("was not recognised"))
    }

    @Test
    fun `revoking a token makes it fail auth`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        val token = ApiCrypto.newToken()
        val hash = ApiCrypto.sha256Hex(token)
        runCatchingBlocking { CreateApiTokenStep.process(CreateApiTokenInput(user.id, hash, "test", null)) }

        // Works before revocation.
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/worlds") { header("Authorization", "Bearer $token") }.status)

        // Revoke through the API.
        val revoke = client.request("/api/v1/auth/token") {
            method = HttpMethod.Delete
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, revoke.status)

        // Now rejected.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/worlds") { header("Authorization", "Bearer $token") }.status)
    }

    @Test
    fun `bearer endpoint rejects a missing token`() = testApplication {
        routing {
            install(AuthPlugin)
            apiV1Routes()
            route("/link") {
                get { call.handleGetLinkPage() }
                post { call.handleApproveLinkPage() }
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/worlds").status)
    }

    private fun <T> runCatchingBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
