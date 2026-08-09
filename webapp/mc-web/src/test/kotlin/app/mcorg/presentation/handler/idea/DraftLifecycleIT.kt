package app.mcorg.presentation.handler.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.draft.CreateDraftStep
import app.mcorg.pipeline.idea.draft.DeleteDraftInput
import app.mcorg.pipeline.idea.draft.DeleteDraftStep
import app.mcorg.pipeline.idea.draft.GetDraftInput
import app.mcorg.pipeline.idea.draft.GetDraftStep
import app.mcorg.pipeline.idea.draft.GetDraftsStep
import app.mcorg.pipeline.idea.draft.UpdateDraftInput
import app.mcorg.pipeline.idea.draft.UpdateDraftStep
import app.mcorg.pipeline.idea.draft.handleCreateDraft
import app.mcorg.pipeline.idea.draft.handleDeleteDraft
import app.mcorg.pipeline.idea.draft.handleGetDraftList
import app.mcorg.pipeline.idea.draft.handleGetDraftWizard
import app.mcorg.pipeline.idea.draft.handlePublishDraft
import app.mcorg.pipeline.idea.draft.handleRevertIdeaToDraft
import app.mcorg.pipeline.idea.draft.handleUpdateDraftStage
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DraftLifecycleIT : WithUser() {

    @BeforeEach
    fun cleanDrafts() {
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.delete("DELETE FROM idea_drafts"),
                parameterSetter = { _, _ -> }
            ).process(Unit)
        }
    }

    // --- Unit-level step tests ---

    @Test
    fun `CreateDraftStep creates draft with BASIC_INFO stage`() = runBlocking {
        val result = CreateDraftStep(user.id).process(Unit)
        assertTrue(result is Result.Success)
        val draftId = result.value
        assertTrue(draftId > 0)

        val draft = GetDraftStep().process(GetDraftInput(draftId, user.id))
        assertTrue(draft is Result.Success)
        assertEquals("BASIC_INFO", draft.value.currentStage)
        assertEquals("{}", draft.value.data)
    }

    @Test
    fun `GetDraftsStep returns empty list when no drafts`() = runBlocking {
        val result = GetDraftsStep(user.id).process(Unit)
        assertTrue(result is Result.Success)
        assertEquals(emptyList(), result.value)
    }

    @Test
    fun `GetDraftsStep returns drafts ordered by updated_at DESC`() = runBlocking {
        val id1 = (CreateDraftStep(user.id).process(Unit) as Result.Success).value
        val id2 = (CreateDraftStep(user.id).process(Unit) as Result.Success).value

        val result = GetDraftsStep(user.id).process(Unit)
        assertTrue(result is Result.Success)
        val drafts = result.value
        assertEquals(2, drafts.size)
        // Most recently created is first
        assertEquals(id2, drafts[0].id)
        assertEquals(id1, drafts[1].id)
    }

    @Test
    fun `UpdateDraftStep merges stage data into JSONB`() = runBlocking {
        val draftId = (CreateDraftStep(user.id).process(Unit) as Result.Success).value

        val stageJson = """{"name":"Iron Farm","description":"Fast iron farm","difficulty":"MID_GAME"}"""
        val updateResult = UpdateDraftStep().process(
            UpdateDraftInput(draftId, user.id, stageJson, "AUTHOR_INFO")
        )
        assertTrue(updateResult is Result.Success)

        val draft = (GetDraftStep().process(GetDraftInput(draftId, user.id)) as Result.Success).value
        assertEquals("AUTHOR_INFO", draft.currentStage)
        assertContains(draft.data, "Iron Farm")
        assertContains(draft.data, "MID_GAME")
    }

    @Test
    fun `UpdateDraftStep merges rather than replaces data`() = runBlocking {
        val draftId = (CreateDraftStep(user.id).process(Unit) as Result.Success).value

        // Set name first
        UpdateDraftStep().process(
            UpdateDraftInput(draftId, user.id, """{"name":"My Build"}""", "AUTHOR_INFO")
        )

        // Add description — name should still be there
        UpdateDraftStep().process(
            UpdateDraftInput(draftId, user.id, """{"description":"A great build"}""", "VERSION_COMPATIBILITY")
        )

        val draft = (GetDraftStep().process(GetDraftInput(draftId, user.id)) as Result.Success).value
        assertContains(draft.data, "My Build")
        assertContains(draft.data, "A great build")
    }

    @Test
    fun `DeleteDraftStep deletes draft by owner`() = runBlocking {
        val draftId = (CreateDraftStep(user.id).process(Unit) as Result.Success).value

        val deleteResult = DeleteDraftStep().process(DeleteDraftInput(draftId, user.id))
        assertTrue(deleteResult is Result.Success)

        val getResult = GetDraftStep().process(GetDraftInput(draftId, user.id))
        assertTrue(getResult is Result.Failure)
    }

    @Test
    fun `DeleteDraftStep does not delete draft owned by other user`() = runBlocking {
        val draftId = (CreateDraftStep(user.id).process(Unit) as Result.Success).value
        val otherUser = createExtraUser("idea_creator")

        val deleteResult = DeleteDraftStep().process(DeleteDraftInput(draftId, otherUser.id))
        // Returns success (0 rows deleted) but draft still exists
        val draft = GetDraftStep().process(GetDraftInput(draftId, user.id))
        assertTrue(draft is Result.Success)
    }

    @Test
    fun `GetDraftStep returns NotFound for non-existent draft`() = runBlocking {
        val result = GetDraftStep().process(GetDraftInput(99999, user.id))
        assertTrue(result is Result.Failure)
    }

    // --- Integration / HTTP tests ---

    @Test
    fun `GET ideas-create redirects to new draft when no drafts exist`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                get { call.handleGetDraftList() }
            }
        }

        // A real redirect, because "New Idea" on the hub is an ordinary link. Answering 200 with
        // an HX-Redirect header rendered a blank page for anyone without drafts yet (MCO-310).
        val response = client.get("/ideas/create") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location)
        assertContains(location, "/ideas/drafts/")
        assertContains(location, "/edit")
    }

    @Test
    fun `GET ideas-create still answers HTMX with an HX-Redirect`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                get { call.handleGetDraftList() }
            }
        }

        val response = client.get("/ideas/create") {
            addAuthCookie(this, ideaCreator)
            header("HX-Request", "true")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers["HX-Redirect"])
    }

    @Test
    fun `GET ideas-create shows draft list when drafts exist`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }
        // A draft only earns a place in the list once it holds something.
        runBlocking {
            UpdateDraftStep().process(
                UpdateDraftInput(draftId, ideaCreator.id, """{"name":"Half-written farm"}""", "BASIC_INFO")
            )
        }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                get { call.handleGetDraftList() }
            }
        }

        val response = client.get("/ideas/create") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "My Drafts")
    }

    @Test
    fun `GET ideas-create reopens a blank draft instead of listing it (MCO-310)`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                get { call.handleGetDraftList() }
            }
        }

        // Every blank draft is the same blank draft. Listing them produced a column of
        // indistinguishable "Untitled Draft" rows after a couple of aborted visits.
        val response = client.get("/ideas/create") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertContains(response.headers[HttpHeaders.Location]!!, "/ideas/drafts/$draftId/edit")
    }

    @Test
    fun `POST ideas-create reuses a blank draft rather than making another (MCO-310)`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                post { call.handleCreateDraft() }
            }
        }

        val response = client.post("/ideas/create") { addAuthCookie(this, ideaCreator) }
        assertContains(response.headers["HX-Redirect"]!!, "/ideas/drafts/$draftId/edit")
        assertEquals(1, runBlocking { (GetDraftsStep(ideaCreator.id).process(Unit) as Result.Success).value.size })
    }

    @Test
    fun `GET ideas-create is open to a user without the publishing role (MCO-291)`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                get { call.handleGetDraftList() }
            }
        }

        // Creating is open to everyone now; only putting a design on the hub is privileged.
        val response = client.get("/ideas/create") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertNotNull(response.headers[HttpHeaders.Location])
    }

    @Test
    fun `POST ideas-create creates draft and redirects`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/ideas/create") {
                post { call.handleCreateDraft() }
            }
        }

        val response = client.post("/ideas/create") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)
        val hxRedirect = response.headers["HX-Redirect"]
        assertNotNull(hxRedirect)
        assertContains(hxRedirect, "/ideas/drafts/")
    }

    @Test
    fun `GET drafts-draftId-edit shows every field on one page (MCO-310)`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                get("/edit") { call.handleGetDraftWizard() }
            }
        }

        val response = client.get("/ideas/drafts/$draftId/edit") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "app-header")

        // Required fields up front...
        assertContains(body, "idea-form__required")
        assertContains(body, """name="name"""")
        assertContains(body, """name="category"""")
        // ...and the optional ones present in the same form rather than on later screens.
        assertContains(body, """name="versionRangeType"""")
        assertContains(body, """name="authorName"""")

        // A nested <form> here would make the parser close the outer one early and orphan the
        // submit button — the fields would render but nothing could be submitted.
        assertEquals(1, Regex("<form").findAll(body).count())
    }

    @Test
    fun `POST drafts-draftId-stage saves data and returns next stage fragment`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/stage") { call.handleUpdateDraftStage() }
            }
        }

        val response = client.submitForm(
            url = "/ideas/drafts/$draftId/stage",
            formParameters = Parameters.build {
                append("currentStage", "BASIC_INFO")
                append("name", "Iron Farm")
                append("description", "A fast iron farm using villagers")
                append("difficulty", "MID_GAME")
            }
        ) { addAuthCookie(this, ideaCreator) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "wizard-stage")
        // Should show AUTHOR_INFO stage content
        assertContains(body, "Author")
    }

    /**
     * The specs block is the whole point of the slim schema (MCO-204), and it is a typed map —
     * which the stage extractor used to drop on the floor, so nothing a user typed there ever
     * reached the draft (and therefore never reached the published idea).
     */
    @Test
    fun `POST drafts-draftId-stage persists typed map fields`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/stage") { call.handleUpdateDraftStage() }
            }
        }

        val response = client.submitForm(
            url = "/ideas/drafts/$draftId/stage",
            formParameters = Parameters.build {
                append("currentStage", "CATEGORY_FIELDS")
                append("category", "TNT")
                append("categoryData.specs.key[]", "TNT per piston")
                append("categoryData.specs.value[]", "10")
                append("categoryData.specs.key[]", "Remaining fuse")
                append("categoryData.specs.value[]", "21gt")
                // The form always carries a trailing blank row — it must not become an entry.
                append("categoryData.specs.key[]", "")
                append("categoryData.specs.value[]", "")
            }
        ) { addAuthCookie(this, ideaCreator) }

        assertEquals(HttpStatusCode.OK, response.status)

        val draft = runBlocking { (GetDraftStep().process(GetDraftInput(draftId, ideaCreator.id)) as Result.Success).value }
        val specs = Json.parseToJsonElement(draft.data)
            .jsonObject["categoryData"]!!.jsonObject["specs"]!!.jsonObject["value"]!!.jsonObject

        assertEquals(2, specs.size)
        assertEquals("10", specs["TNT per piston"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("21gt", specs["Remaining fuse"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE drafts-draftId discards draft`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                delete { call.handleDeleteDraft() }
            }
        }

        val response = client.delete("/ideas/drafts/$draftId") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)

        // Draft should no longer exist
        val draft = runBlocking { GetDraftStep().process(GetDraftInput(draftId, ideaCreator.id)) }
        assertTrue(draft is Result.Failure)
    }

    @Test
    fun `draft persists across server restart (data survives in DB)`() = runBlocking {
        val draftId = (CreateDraftStep(user.id).process(Unit) as Result.Success).value

        UpdateDraftStep().process(
            UpdateDraftInput(draftId, user.id, """{"name":"Persistent Farm"}""", "AUTHOR_INFO")
        )

        // Simulate restart by re-querying
        val draft = (GetDraftStep().process(GetDraftInput(draftId, user.id)) as Result.Success).value
        assertContains(draft.data, "Persistent Farm")
        assertEquals("AUTHOR_INFO", draft.currentStage)
    }

    // --- Publish endpoint tests ---

    @Test
    fun `POST publish succeeds with complete draft and redirects to idea`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }
        runBlocking { populateCompleteDraft(draftId, ideaCreator.id) }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/publish") { call.handlePublishDraft() }
            }
        }

        val response = client.post("/ideas/drafts/$draftId/publish") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)
        val hxRedirect = response.headers["HX-Redirect"]
        assertNotNull(hxRedirect)
        assertContains(hxRedirect, "/ideas/")

        // Draft should be deleted
        val draft = runBlocking { GetDraftStep().process(GetDraftInput(draftId, ideaCreator.id)) }
        assertTrue(draft is Result.Failure)
    }

    @Test
    fun `POST publish with incomplete draft returns 400 and draft still exists`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        // Create draft with only a name — missing required fields
        val draftId = runBlocking { (CreateDraftStep(ideaCreator.id).process(Unit) as Result.Success).value }
        runBlocking {
            UpdateDraftStep().process(
                UpdateDraftInput(draftId, ideaCreator.id, """{"name":"Incomplete Farm"}""", "BASIC_INFO")
            )
        }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/publish") { call.handlePublishDraft() }
            }
        }

        val response = client.post("/ideas/drafts/$draftId/publish") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.BadRequest, response.status)

        // Draft should still exist
        val draft = runBlocking { GetDraftStep().process(GetDraftInput(draftId, ideaCreator.id)) }
        assertTrue(draft is Result.Success)
    }

    @Test
    fun `POST publish without the publishing role yields a PRIVATE idea (MCO-291)`() = testApplication {
        val draftId = runBlocking { (CreateDraftStep(user.id).process(Unit) as Result.Success).value }
        runBlocking { populateCompleteDraft(draftId, user.id) }

        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/publish") { call.handlePublishDraft() }
            }
        }

        // `user` has no publishing role — that no longer blocks turning a draft into an idea, it
        // only keeps the result off the community hub.
        val response = client.post("/ideas/drafts/$draftId/publish") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, response.status)

        val ideaId = response.headers["HX-Redirect"]!!.substringAfterLast('/').toInt()
        val idea = runBlocking { (GetIdeaStep.process(ideaId) as Result.Success).value }
        assertEquals(IdeaVisibility.PRIVATE, idea.visibility)
    }

    @Test
    fun `POST publish without authentication returns redirect`() = testApplication {
        routing {
            install(AuthPlugin)
            route("/ideas/drafts/{draftId}") {
                post("/publish") { call.handlePublishDraft() }
            }
        }

        val client = createClient { followRedirects = false }
        val response = client.post("/ideas/drafts/1/publish")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // --- Revert endpoint tests ---

    @Test
    fun `POST revert by owner returns clientRedirect to draft`() = testApplication {
        val ideaCreator = createExtraUser("idea_creator")
        val ideaId = runBlocking { createIdea("Iron Farm", IdeaCategory.FARM, ideaCreator.id) }

        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                route("/revert") {
                    post { call.handleRevertIdeaToDraft() }
                }
            }
        }

        val response = client.post("/ideas/$ideaId/revert") { addAuthCookie(this, ideaCreator) }
        assertEquals(HttpStatusCode.OK, response.status)
        val hxRedirect = response.headers["HX-Redirect"]
        assertNotNull(hxRedirect)
        assertContains(hxRedirect, "/ideas/drafts/")
        assertContains(hxRedirect, "/edit")
    }

    @Test
    fun `POST revert by non-owner returns 403`() = testApplication {
        val owner = createExtraUser("idea_creator")
        val other = createExtraUser("idea_creator")
        val ideaId = runBlocking { createIdea("Iron Farm", IdeaCategory.FARM, owner.id) }

        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                route("/revert") {
                    post { call.handleRevertIdeaToDraft() }
                }
            }
        }

        val response = client.post("/ideas/$ideaId/revert") { addAuthCookie(this, other) }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `POST revert without authentication returns redirect`() = testApplication {
        val owner = createExtraUser("idea_creator")
        val ideaId = runBlocking { createIdea("Iron Farm", IdeaCategory.FARM, owner.id) }

        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                route("/revert") {
                    post { call.handleRevertIdeaToDraft() }
                }
            }
        }

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.post("/ideas/$ideaId/revert")
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // --- Helpers ---

    private fun createIdea(name: String, category: IdeaCategory, createdBy: Int = user.id): Int = runBlocking {
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
                stmt.setInt(7, createdBy)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private suspend fun populateCompleteDraft(draftId: Int, userId: Int) {
        val completeData = """
            {
                "name": "Iron Farm",
                "description": "A fast iron farm using villagers and golems",
                "difficulty": "MID_GAME",
                "category": "FARM",
                "author": {"type": "app.mcorg.domain.model.idea.Author.SingleAuthor", "name": "evegul"},
                "versionRange": {"type": "app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"},
                "categoryData": {
                    "howToUse": {"type": "app.mcorg.domain.model.idea.schema.CategoryValue.TextValue", "value": "Place and wait"},
                    "playersRequired": {"type": "app.mcorg.domain.model.idea.schema.CategoryValue.TextValue", "value": "1"}
                }
            }
        """.trimIndent()
        UpdateDraftStep().process(UpdateDraftInput(draftId, userId, completeData, "REVIEW"))
    }
}
