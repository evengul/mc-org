package app.mcorg.presentation.plugins

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.router.configureAppRouter
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-158 / MCO-436 — what a route plugin's refusal looks like to the person who triggered it.
 *
 * Two readers, two shapes. Typing a URL or following a link must land on a status page with the
 * app's chrome; before this, `/worlds/{id}/projects/settings` answered a bare "Invalid or missing
 * project ID" and a member opening world settings got one plain-text sentence. An HTMX request
 * must instead get the standard alert, retargeted at the alert container: the body carries
 * `hx-ext="response-targets"`, which swaps a non-200 response only when it names a target, so a
 * refusal without `HX-Retarget` changes nothing on screen.
 *
 * Runs through the real router rather than a hand-built route tree, so the plugin order under
 * test is the one production has — including the siblings that run after an earlier plugin has
 * already answered.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PluginRefusalIT : WithUser() {

    private var worldId = 0

    @BeforeAll
    fun createWorld() {
        worldId = createWorldOwnedByUser()
    }

    // --- Param plugins: a path segment that is not an id --------------------------------------

    @Test
    fun `a non-numeric project id renders the 404 page on direct navigation`() = testApplication {
        val response = app().get("/worlds/$worldId/projects/settings") { addAuthCookie(this) }

        assertStatusPage(response, HttpStatusCode.NotFound, "404 — Not Found")
    }

    @Test
    fun `a non-numeric project id answers an HTMX request with a visible alert`() = testApplication {
        val response = app().get("/worlds/$worldId/projects/settings") { addAuthCookie(this); htmx() }

        assertAlert(response, HttpStatusCode.NotFound)
    }

    @Test
    fun `a non-numeric world id renders the 404 page on direct navigation`() = testApplication {
        val response = app().get("/worlds/not-a-world/roadmap") { addAuthCookie(this) }

        assertStatusPage(response, HttpStatusCode.NotFound, "404 — Not Found")
    }

    @Test
    fun `a refused world id does not crash the plugins after it`() = testApplication {
        // Ktor skips the route handler once a plugin has answered, but not the sibling plugins.
        // WorldParticipantPlugin then read a world id WorldParamPlugin never set and threw: the
        // refusal went out as a 500 and an ERROR line.
        val logger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val response = app().get("/worlds/not-a-world/roadmap") { addAuthCookie(this) }

            assertTrue(response.status.value < 500, "a refusal, not a crash; was ${response.status}")
            val unhandled = appender.list.filter { it.formattedMessage.startsWith("Unhandled exception") }
            assertTrue(unhandled.isEmpty(), "should not throw: ${unhandled.map { it.formattedMessage }}")
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `a signed-out visit to admin is redirected without an exception`() = testApplication {
        // AuthPlugin redirects and stores no user; AdminPlugin then asked for one.
        val response = assertNoUnhandledException { app().get("/admin") }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `a signed-out visit to a bad world id is redirected without an exception`() = testApplication {
        val response = assertNoUnhandledException { app().get("/worlds/not-a-world/roadmap") }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `an unknown idea under the comment routes is one clean 404`() = testApplication {
        // IdeaParamPlugin answers 404; IdeaVisibilityPlugin and IdeaCommentParamPlugin come after.
        val response = assertNoUnhandledException {
            app().delete("/ideas/987654321/comments/1") { addAuthCookie(this) }
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a project id that does not exist answers an HTMX request with the alert, not the whole 404 page`() = testApplication {
        // StatusPages used to replace every 404 body with the full page, so this alert reached
        // htmx as an <html> document and was swapped into the alert list.
        val response = app().get("/worlds/$worldId/projects/987654321") { addAuthCookie(this); htmx() }

        assertAlert(response, HttpStatusCode.NotFound)
    }

    // --- Role plugins -------------------------------------------------------------------------

    @Test
    fun `a member opening world settings gets the 403 page on direct navigation`() = testApplication {
        val member = memberOf(worldId, Role.MEMBER)

        val response = app().get("/worlds/$worldId/settings") { addAuthCookie(this, member) }

        assertStatusPage(response, HttpStatusCode.Forbidden, "403 — Forbidden")
    }

    @Test
    fun `a member opening world settings over HTMX gets the alert`() = testApplication {
        val member = memberOf(worldId, Role.MEMBER)

        val response = app().get("/worlds/$worldId/settings") { addAuthCookie(this, member); htmx() }

        assertAlert(response, HttpStatusCode.Forbidden)
    }

    @Test
    fun `a non-member opening a world gets the 403 page on direct navigation`() = testApplication {
        val outsider = createExtraUser()

        val response = app().get("/worlds/$worldId/roadmap") { addAuthCookie(this, outsider) }

        assertStatusPage(response, HttpStatusCode.Forbidden, "403 — Forbidden")
    }

    @Test
    fun `an admin deleting the world over HTMX is told only the owner can`() = testApplication {
        val admin = memberOf(worldId, Role.ADMIN)

        val response = app().delete("/worlds/$worldId/settings") { addAuthCookie(this, admin); htmx() }

        assertAlert(response, HttpStatusCode.Forbidden)
        assertTrue(response.bodyAsText().contains("owner"), "should say who may delete a world")
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun ApplicationTestBuilder.app() = run {
        application {
            configureStatusStaticRouter()
            configureAppRouter()
        }
        createClient { followRedirects = false }
    }

    private fun HttpRequestBuilder.htmx() = header("HX-Request", "true")

    /**
     * Off the root logger, so both the error boundary's "Unhandled exception" line and Ktor's own
     * logging of a response sent twice are seen.
     */
    private suspend fun assertNoUnhandledException(request: suspend () -> HttpResponse): HttpResponse {
        val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val response = request()
            val thrown = appender.list.filter { it.level.isGreaterOrEqual(Level.ERROR) || it.throwableProxy != null }
            assertTrue(
                thrown.isEmpty(),
                "should not throw: ${thrown.map { "${it.formattedMessage} / ${it.throwableProxy?.className}" }}",
            )
            return response
        } finally {
            logger.detachAppender(appender)
        }
    }

    private suspend fun assertStatusPage(response: HttpResponse, status: HttpStatusCode, heading: String) {
        val body = response.bodyAsText()
        assertEquals(status, response.status, "body was: ${body.take(300)}")
        assertTrue(body.contains(heading), "should render the '$heading' status page; was: ${body.take(300)}")
        assertTrue(body.contains("error-page__card"), "should be the full status page with chrome")
        assertNull(response.headers["HX-Retarget"], "a page load has no use for HX-Retarget")
    }

    private suspend fun assertAlert(response: HttpResponse, status: HttpStatusCode) {
        val body = response.bodyAsText()
        assertEquals(status, response.status, "body was: ${body.take(300)}")
        assertEquals("#alert-container", response.headers["HX-Retarget"], "an HTMX refusal must name a target or it is never swapped")
        assertTrue(body.contains("alert"), "should be the standard alert; was: ${body.take(300)}")
        assertFalse(body.contains("<html", ignoreCase = true), "a fragment, not a whole page; was: ${body.take(300)}")
    }

    private fun createWorldOwnedByUser(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "PluginRefusalIT ${System.nanoTime()}",
                description = "refusals",
                version = MinecraftVersion.fromString("1.21.4"),
            )
        )
        (result as Result.Success).value
    }

    private fun memberOf(worldId: Int, role: Role) = createExtraUser().also { member ->
        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, member.id)
                    stmt.setInt(2, worldId)
                    stmt.setString(3, "member-${member.id}")
                    stmt.setInt(4, role.level)
                }
            ).process(Unit)
        }
        CacheManager.onMemberAdded(member.id, worldId)
    }
}
