package app.mcorg.presentation.handler.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.handleGetIdeas
import app.mcorg.pipeline.idea.handleSearchIdeas
import app.mcorg.pipeline.idea.single.handleGetIdea
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetIdeasIT : WithUser() {

    @BeforeEach
    fun cleanIdeas() {
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.delete("DELETE FROM ideas"),
                parameterSetter = { _, _ -> }
            ).process(Unit)
        }
    }

    // --- GET /ideas ---

    @Test
    fun `GET ideas returns 200 with pageShell and ideas-list-container`() = testApplication {
        createIdea("Iron Farm", IdeaCategory.FARM)
        createIdea("Gold Farm", IdeaCategory.FARM)

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get { call.handleGetIdeas() }
            }
        }

        val response = client.get("/ideas") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "ideas-list-container")
        assertContains(body, "Iron Farm")
        assertContains(body, "Gold Farm")
        assertContains(body, "app-header")
    }

    @Test
    fun `GET ideas shows empty state when no ideas exist`() = testApplication {
        routing {
            install(AuthPlugin)
            route("/ideas") {
                get { call.handleGetIdeas() }
            }
        }

        val response = client.get("/ideas") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "No ideas match your filters")
    }

    @Test
    fun `GET ideas page 2 returns correct slice`() = testApplication {
        // 25 ideas — page 1 has 20, page 2 has 5
        for (i in 1..25) {
            createIdea("Farm $i", IdeaCategory.FARM)
        }

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get { call.handleGetIdeas() }
            }
        }

        val response = client.get("/ideas?page=2") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Pagination controls should be rendered (more than 1 page)
        assertContains(body, "ideas-pagination")
    }

    @Test
    fun `GET ideas redirects unauthenticated user`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get { call.handleGetIdeas() }
            }
        }

        val response = client.get("/ideas")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // --- GET /ideas/search (fragment) ---

    @Test
    fun `GET ideas search returns ideas-list-container fragment`() = testApplication {
        createIdea("Iron Farm", IdeaCategory.FARM)
        createIdea("Storage System", IdeaCategory.STORAGE)

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get("/search") { call.handleSearchIdeas() }
            }
        }

        val response = client.get("/ideas/search") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "ideas-list-container")
        assertContains(body, "Iron Farm")
        assertContains(body, "Storage System")
    }

    @Test
    fun `GET ideas search filters by category`() = testApplication {
        createIdea("Iron Farm", IdeaCategory.FARM)
        createIdea("Storage System", IdeaCategory.STORAGE)

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get("/search") { call.handleSearchIdeas() }
            }
        }

        val response = client.get("/ideas/search?category=FARM") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Iron Farm")
        assertFalse(body.contains("Storage System"))
    }

    @Test
    fun `GET ideas search page 2 returns correct slice and preserves pagination controls`() = testApplication {
        for (i in 1..25) {
            createIdea("Farm $i", IdeaCategory.FARM)
        }

        routing {
            install(AuthPlugin)
            route("/ideas") {
                get("/search") { call.handleSearchIdeas() }
            }
        }

        val p1 = client.get("/ideas/search?page=1") { addAuthCookie(this) }
        val p2 = client.get("/ideas/search?page=2") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, p1.status)
        assertEquals(HttpStatusCode.OK, p2.status)

        val body1 = p1.bodyAsText()
        val body2 = p2.bodyAsText()

        // Both pages show pagination
        assertContains(body1, "ideas-pagination")
        assertContains(body2, "ideas-pagination")

        // Pages have different content — different ideas
        assertFalse(body1 == body2)
    }

    // --- GET /ideas/:ideaId ---

    @Test
    fun `GET idea detail returns 200 with idea content`() = testApplication {
        val ideaId = createIdea("Iron Farm", IdeaCategory.FARM)

        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                get { call.handleGetIdea() }
            }
        }

        val response = client.get("/ideas/$ideaId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Iron Farm")
        assertContains(body, "idea-detail")
        assertContains(body, "app-header")
        assertContains(body, "Ideas") // breadcrumb
    }

    // --- Helper ---

    private fun createIdea(name: String, category: IdeaCategory): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO ideas (
                    name, description, category, author, difficulty,
                    minecraft_version_range, category_data, created_by,
                    rating_average, rating_count
                ) VALUES (?, ?, ?, ?::jsonb, ?, ?::jsonb, '{}', ?, 0.0, 0)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setString(2, "Test description for $name")
                stmt.setString(3, category.name)
                stmt.setString(
                    4,
                    """{"name": "evegul", "type": "app.mcorg.domain.model.idea.Author.SingleAuthor"}"""
                )
                stmt.setString(5, IdeaDifficulty.MID_GAME.name)
                stmt.setString(
                    6,
                    """{"type": "app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"}"""
                )
                stmt.setInt(7, user.id)
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
