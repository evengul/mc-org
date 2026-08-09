package app.mcorg.presentation.handler.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.pipeline.idea.single.handleGetIdea
import app.mcorg.pipeline.idea.single.handlePublishIdeaToHub
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.presentation.plugins.IdeaPublisherPlugin
import app.mcorg.presentation.plugins.IdeaVisibilityPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

/**
 * MCO-291: a private idea is a personal design, and putting one on the community hub is the
 * privileged step — replacing the old role gate on *creating* an idea.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class IdeaVisibilityIT : WithUser() {

    @BeforeEach
    fun cleanIdeas() {
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.delete("DELETE FROM ideas"),
                parameterSetter = { _, _ -> }
            ).process(Unit)
        }
    }

    private fun ApplicationTestBuilder.installIdeaRoutes() {
        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                install(IdeaVisibilityPlugin)
                get { call.handleGetIdea() }
                route("/public") {
                    install(IdeaPublisherPlugin)
                    patch { call.handlePublishIdeaToHub() }
                }
            }
        }
    }

    // --- Reading ---

    @Test
    fun `a stranger gets 404 for someone else's private design`() = testApplication {
        val owner = createExtraUser()
        val ideaId = createIdea(IdeaVisibility.PRIVATE, owner)
        installIdeaRoutes()

        val response = client.get("/ideas/$ideaId") { addAuthCookie(this) }

        // 404 rather than 403 — "exists but not yours" leaks every private design.
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `the owner can read their own private design`() = testApplication {
        val ideaId = createIdea(IdeaVisibility.PRIVATE, user)
        installIdeaRoutes()

        val response = client.get("/ideas/$ideaId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `anyone can read a public idea`() = testApplication {
        val owner = createExtraUser()
        val ideaId = createIdea(IdeaVisibility.PUBLIC, owner)
        installIdeaRoutes()

        val response = client.get("/ideas/$ideaId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // --- Publishing ---

    @Test
    fun `publishing without the role is forbidden`() = testApplication {
        val ideaId = createIdea(IdeaVisibility.PRIVATE, user)
        installIdeaRoutes()

        val response = client.patch("/ideas/$ideaId/public") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IdeaVisibility.PRIVATE, visibilityOf(ideaId))
    }

    @Test
    fun `the owner with the role can publish to the hub`() = testApplication {
        val publisher = createExtraUser("idea_creator")
        val ideaId = createIdea(IdeaVisibility.PRIVATE, publisher)
        installIdeaRoutes()

        val response = client.patch("/ideas/$ideaId/public") { addAuthCookie(this, publisher) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(IdeaVisibility.PUBLIC, visibilityOf(ideaId))
    }

    @Test
    fun `the role does not let you publish someone else's design`() = testApplication {
        val owner = createExtraUser()
        val publisher = createExtraUser("idea_creator")
        val ideaId = createIdea(IdeaVisibility.PRIVATE, owner)
        installIdeaRoutes()

        // Stopped by the visibility gate before ownership is ever considered.
        val response = client.patch("/ideas/$ideaId/public") { addAuthCookie(this, publisher) }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(IdeaVisibility.PRIVATE, visibilityOf(ideaId))
    }

    @Test
    fun `the role does not let you publish someone else's public-hub candidate`() = testApplication {
        // Same check, but on an idea the publisher is allowed to *see* — isolating the ownership
        // rule from the visibility rule.
        val owner = createExtraUser()
        val publisher = createExtraUser("idea_creator")
        val ideaId = createIdea(IdeaVisibility.PUBLIC, owner)
        installIdeaRoutes()

        val response = client.patch("/ideas/$ideaId/public") { addAuthCookie(this, publisher) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun visibilityOf(ideaId: Int): IdeaVisibility = runBlocking {
        (GetIdeaStep.process(ideaId) as Result.Success).value.visibility
    }

    private fun createIdea(visibility: IdeaVisibility, owner: TokenProfile): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO ideas (
                    name, description, category, author, difficulty,
                    minecraft_version_range, category_data, created_by,
                    rating_average, rating_count, visibility
                ) VALUES (?, 'A design', ?, ?::jsonb, ?, ?::jsonb, '{}', ?, 0.0, 0, ?)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, "Design ${visibility.name}")
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
                stmt.setString(7, visibility.name)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
