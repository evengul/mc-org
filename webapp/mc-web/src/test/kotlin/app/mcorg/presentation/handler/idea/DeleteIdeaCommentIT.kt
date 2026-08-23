package app.mcorg.presentation.handler.idea

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.single.handleDeleteIdeaComment
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaCommentAuthorPlugin
import app.mcorg.presentation.plugins.IdeaCommentParamPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.presentation.plugins.IdeaVisibilityPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.delete
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-351 — the comment-delete IDOR.
 *
 * `DELETE FROM idea_comments WHERE id = ?` was the entire authorization story: no ownership
 * check, and a param plugin that only asked whether the comment existed, not whether it belonged
 * to the idea in the path. `DELETE /ideas/1/comments/{n}` in a loop wiped the hub's comment and
 * rating history. The Delete button was hidden unless `commenterId == userId`, which is the
 * classic tell that the check never made it server-side.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DeleteIdeaCommentIT : WithUser() {

    @BeforeEach
    fun cleanIdeas() {
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.delete("DELETE FROM ideas"),
                parameterSetter = { _, _ -> }
            ).process(Unit)
        }
        // The existence cache is keyed on (ideaId, commentId) and survives between tests in the
        // reused surefire JVM; a stale positive would mask exactly the bug under test.
        CacheManager.ideaCommentExists.invalidateAll()
    }

    private fun ApplicationTestBuilder.installCommentRoutes() {
        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                install(IdeaVisibilityPlugin)
                route("/comments/{commentId}") {
                    install(IdeaCommentParamPlugin)
                    method(HttpMethod.Delete) {
                        install(IdeaCommentAuthorPlugin)
                        handle { call.handleDeleteIdeaComment() }
                    }
                }
            }
        }
    }

    @Test
    fun `the comment author can delete their own comment`() = testApplication {
        val ideaId = createIdea(user)
        val commentId = createComment(ideaId, user)
        installCommentRoutes()

        val response = client.delete("/ideas/$ideaId/comments/$commentId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(commentIsGone(commentId), "the author's own comment should actually be deleted")
    }

    @Test
    fun `a different signed-in user cannot delete someone else's comment`() = testApplication {
        val author = createExtraUser()
        val ideaId = createIdea(author)
        val commentId = createComment(ideaId, author)
        installCommentRoutes()

        // `user` is neither the comment's author nor the idea's owner.
        val response = client.delete("/ideas/$ideaId/comments/$commentId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(!commentIsGone(commentId), "a refused delete must not have deleted anything")
    }

    @Test
    fun `the idea owner can delete a comment left on their idea`() = testApplication {
        val commenter = createExtraUser()
        val ideaId = createIdea(user)
        val commentId = createComment(ideaId, commenter)
        installCommentRoutes()

        val response = client.delete("/ideas/$ideaId/comments/$commentId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(commentIsGone(commentId), "the idea owner should be able to moderate their own idea")
    }

    @Test
    fun `a superadmin can delete any comment`() = testApplication {
        val author = createExtraUser()
        val ideaId = createIdea(author)
        val commentId = createComment(ideaId, author)
        val admin = createExtraUser("superadmin")
        installCommentRoutes()

        val response = client.delete("/ideas/$ideaId/comments/$commentId") { addAuthCookie(this, admin) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(commentIsGone(commentId), "a superadmin should be able to moderate the hub")
    }

    @Test
    fun `a valid comment id under the wrong idea is not found`() = testApplication {
        // The original IDOR: the param plugin checked only that the comment existed, so any
        // visible idea id paired with any comment id.
        val author = createExtraUser()
        val theirIdea = createIdea(author)
        val commentId = createComment(theirIdea, author)
        val myIdea = createIdea(user)
        installCommentRoutes()

        val response = client.delete("/ideas/$myIdea/comments/$commentId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(!commentIsGone(commentId), "the comment must survive a mismatched idea id")
    }

    @Test
    fun `a mismatched comment id is refused without an unhandled exception`() = testApplication {
        // The status code above was always right; what it could not see is that the *author*
        // plugin still ran after the param plugin had 404'd, because Ktor's isHandled guard covers
        // the route handler but not sibling onCall interceptors. getIdeaCommentId() then threw
        // `No instance for key AttributeKey: IdeaCommentParam` — a stack trace per request, free
        // to any signed-in user, and invisible to a status-code assertion.
        //
        // Captured off the root logger rather than the app's error boundary: this test installs
        // routes without StatusPages, so an escaping exception surfaces through Ktor's own engine
        // logging, which is exactly where it was first observed.
        val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val author = createExtraUser()
            val theirIdea = createIdea(author)
            val commentId = createComment(theirIdea, author)
            val myIdea = createIdea(user)
            installCommentRoutes()

            val response = client.delete("/ideas/$myIdea/comments/$commentId") { addAuthCookie(this) }

            assertEquals(HttpStatusCode.NotFound, response.status)

            val thrown = appender.list.filter { event ->
                event.throwableProxy?.className?.contains("IllegalStateException") == true ||
                    event.formattedMessage.contains("No instance for key")
            }
            assertTrue(
                thrown.isEmpty(),
                "a mismatched id should be a clean 404, but the request threw: " +
                    thrown.map { "${it.formattedMessage} / ${it.throwableProxy?.className}" },
            )
        } finally {
            logger.detachAppender(appender)
        }
    }

    private fun commentIsGone(commentId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT NOT EXISTS(SELECT 1 FROM idea_comments WHERE id = ?)"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next() && rs.getBoolean(1) },
        ).process(commentId)
        (result as Result.Success).value
    }

    private fun createComment(ideaId: Int, commenter: TokenProfile): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO idea_comments (idea_id, commenter_id, commenter_name, content)
                VALUES (?, ?, ?, 'Nice build') RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setInt(2, commenter.id)
                stmt.setString(3, commenter.minecraftUsername)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createIdea(owner: TokenProfile): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO ideas (
                    name, description, category, author, difficulty,
                    minecraft_version_range, category_data, created_by,
                    rating_average, rating_count, visibility
                ) VALUES (?, 'A design', ?, ?::jsonb, ?, ?::jsonb, '{}', ?, 0.0, 0, 'PUBLIC')
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, "Design for ${owner.id}")
                stmt.setString(2, IdeaCategory.FARM.name)
                stmt.setString(
                    3,
                    """{"name": "evegul", "type": "app.mcorg.domain.model.idea.Author.SingleAuthor"}"""
                )
                stmt.setString(4, IdeaDifficulty.MID_GAME.name)
                stmt.setString(
                    5,
                    """{"type": "app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"}"""
                )
                stmt.setInt(6, owner.id)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
