package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.handleClearOverride
import app.mcorg.pipeline.resources.handleGetDrillChain
import app.mcorg.pipeline.resources.handleGetNodePicker
import app.mcorg.pipeline.resources.handlePinSource
import app.mcorg.pipeline.resources.handleResolveTagMember
import app.mcorg.pipeline.resources.handleUpdatePlanProgress
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration tests for the plan chain drill endpoints:
 *
 *   GET  /plan/chain/{itemId}              — drill view (Phase 1, unchanged)
 *   GET  /plan/chain/{itemId}/sources      — node picker
 *   POST /plan/chain/{itemId}/pin          — pin source override
 *   POST /plan/chain/{itemId}/tag          — resolve tag member override
 *   DELETE /plan/chain/{itemId}/override   — clear override
 *
 * The Testcontainers DB has no ingested Minecraft graph, so real plan derivation
 * fails gracefully. The override persistence tests assert DB state directly via
 * query helpers — the re-render may fall back to the not-found fragment but the
 * DB row change is the real behaviour under test.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PlanChainIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
        projectId = createProject(worldId)
        // Add a resource row so param plugins resolve (project exists)
        createResourceGathering(projectId)
    }

    // -------------------------------------------------------------------------
    // Auth — GET chain
    // -------------------------------------------------------------------------

    @Test
    fun `GET chain without auth returns redirect`() = testApplication {
        setupAllRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Graceful fallback — no Minecraft graph in Testcontainers DB
    // -------------------------------------------------------------------------

    @Test
    fun `GET chain responds 200 with fallback fragment when plan cannot be derived`() = testApplication {
        setupAllRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Fragment always replaces #project-content
        assertContains(body, "project-content")
        // Back-to-plan button is always rendered
        assertContains(body, "Back to plan")
        // Fallback callout is rendered (plan derivation failed — no graph)
        assertContains(body, "callout")
    }

    @Test
    fun `GET chain with an item that has no resources defined returns graceful fragment`() = testApplication {
        // Empty project: no resource_gathering rows → ValidationError from GenerateGatheringPlanStep
        val emptyProjectId = createProject(worldId)
        setupAllRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$emptyProjectId/plan/chain/minecraft:oak_log"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-content")
        assertContains(body, "Back to plan")
        assertContains(body, "callout")
    }

    @Test
    fun `GET chain with URL-encoded tag id responds 200 with graceful fallback`() = testApplication {
        setupAllRoutes()

        // Tag ids have a '#' prefix — must be percent-encoded in the URL
        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/%23minecraft:planks"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-content")
    }

    @Test
    fun `GET chain back-to-plan link points to list lens`() = testApplication {
        setupAllRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Back button targets the list lens fragment
        assertContains(body, "detail-content?lens=list")
    }

    // -------------------------------------------------------------------------
    // GET /sources — node picker
    // -------------------------------------------------------------------------

    @Test
    fun `GET sources without auth returns redirect`() = testApplication {
        setupAllRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/sources?node=minecraft:iron_ingot"
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `GET sources without node param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/sources"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET sources with missing plan returns graceful picker fragment`() = testApplication {
        setupAllRoutes()

        // No graph — plan derivation fails; picker falls back gracefully
        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/sources?node=minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        // Returns 200 with a graceful fallback picker fragment (plan not available)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Must return some HTML (graceful — not a crash)
        assertContains(body, "picker")
    }

    @Test
    fun `GET sources with a tag node id that is absent from plan returns graceful picker`() = testApplication {
        setupAllRoutes()

        // Tag node ("#minecraft:planks") may be absent from the derived tree when it has been
        // resolved to a concrete member. In the Testcontainers DB there is no graph, so the
        // synthesize path can't find the tag, but the endpoint must still respond gracefully.
        val response = client.get(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/sources?node=%23minecraft:planks"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Must return a picker-family fragment, never a 500
        assertContains(body, "picker")
    }

    // -------------------------------------------------------------------------
    // POST /pin — source override persistence
    // -------------------------------------------------------------------------

    @Test
    fun `POST pin without auth returns redirect`() = testApplication {
        setupAllRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/pin",
            formParameters = Parameters.build {
                append("node", "minecraft:iron_ingot")
                append("sourceKey", "minecraft:block:blocks/iron_ore.json")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `POST pin with missing node param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/pin",
            formParameters = Parameters.build {
                append("sourceKey", "minecraft:block:blocks/iron_ore.json")
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST pin with missing sourceKey param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/pin",
            formParameters = Parameters.build {
                append("node", "minecraft:iron_ingot")
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST pin persists source override row in DB`() = testApplication {
        val pid = createProject(worldId)
        createResourceGathering(pid)
        setupAllRoutes()

        val nodeId = "minecraft:iron_ingot"
        val sourceKey = "minecraft:block:blocks/iron_ore.json"

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$pid/plan/chain/minecraft:iron_ingot/pin",
            formParameters = Parameters.build {
                append("node", nodeId)
                append("sourceKey", sourceKey)
            }
        ) {
            addAuthCookie(this)
        }

        // Re-render returns 200 (may be fallback fragment since no graph)
        assertEquals(HttpStatusCode.OK, response.status)
        // DB row persisted
        val override = getOverrideRow(pid, nodeId)
        assertNotNull(override, "Expected override row in DB after POST /pin")
        assertEquals(sourceKey, override.sourceKey)
        assertNull(override.tagMember)
    }

    @Test
    fun `POST pin re-render response is HTML fragment`() = testApplication {
        val pid = createProject(worldId)
        createResourceGathering(pid)
        setupAllRoutes()

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$pid/plan/chain/minecraft:iron_ingot/pin",
            formParameters = Parameters.build {
                append("node", "minecraft:iron_ingot")
                append("sourceKey", "minecraft:block:blocks/iron_ore.json")
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // Always renders into #project-content
        assertContains(response.bodyAsText(), "project-content")
    }

    // -------------------------------------------------------------------------
    // POST /tag — tag member override persistence
    // -------------------------------------------------------------------------

    @Test
    fun `POST tag without auth returns redirect`() = testApplication {
        setupAllRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/%23minecraft:planks/tag",
            formParameters = Parameters.build {
                append("node", "#minecraft:planks")
                append("memberItemId", "minecraft:oak_planks")
            }
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `POST tag with missing node param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/%23minecraft:planks/tag",
            formParameters = Parameters.build {
                append("memberItemId", "minecraft:oak_planks")
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST tag with missing memberItemId param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$projectId/plan/chain/%23minecraft:planks/tag",
            formParameters = Parameters.build {
                append("node", "#minecraft:planks")
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST tag persists tag member override row in DB`() = testApplication {
        val pid = createProject(worldId)
        createResourceGathering(pid)
        setupAllRoutes()

        val tagId = "#minecraft:planks"
        val memberId = "minecraft:oak_planks"

        val response = client.submitForm(
            url = "/worlds/$worldId/projects/$pid/plan/chain/minecraft:iron_ingot/tag",
            formParameters = Parameters.build {
                append("node", tagId)
                append("memberItemId", memberId)
            }
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // DB row persisted
        val override = getOverrideRow(pid, tagId)
        assertNotNull(override, "Expected override row in DB after POST /tag")
        assertEquals(memberId, override.tagMember)
        assertNull(override.sourceKey)
    }

    // -------------------------------------------------------------------------
    // DELETE /override — clear override
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE override without auth returns redirect`() = testApplication {
        setupAllRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.delete(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/override?node=minecraft:iron_ingot"
        )

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `DELETE override without node param returns 400`() = testApplication {
        setupAllRoutes()

        val response = client.delete(
            "/worlds/$worldId/projects/$projectId/plan/chain/minecraft:iron_ingot/override"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE override clears previously pinned source from DB`() = testApplication {
        val pid = createProject(worldId)
        createResourceGathering(pid)
        setupAllRoutes()

        val nodeId = "minecraft:iron_ingot"
        val sourceKey = "minecraft:block:blocks/iron_ore.json"

        // First pin a source
        seedOverrideSource(pid, nodeId, sourceKey)
        assertNotNull(getOverrideRow(pid, nodeId), "Seed failed — override row should exist before DELETE")

        // Now clear it
        val response = client.delete(
            "/worlds/$worldId/projects/$pid/plan/chain/minecraft:iron_ingot/override?node=$nodeId"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        // Row removed from DB
        assertNull(getOverrideRow(pid, nodeId), "Override row should be cleared after DELETE /override")
    }

    @Test
    fun `DELETE override re-render response is HTML fragment`() = testApplication {
        val pid = createProject(worldId)
        createResourceGathering(pid)
        setupAllRoutes()

        val response = client.delete(
            "/worlds/$worldId/projects/$pid/plan/chain/minecraft:iron_ingot/override?node=minecraft:iron_ingot"
        ) {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "project-content")
    }

    // -------------------------------------------------------------------------
    // Route helper — sets up all chain sub-routes
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupAllRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/plan/chain/{itemId}") {
                        get { call.handleGetDrillChain() }
                        get("/sources") { call.handleGetNodePicker() }
                        post("/pin") { call.handlePinSource() }
                        post("/tag") { call.handleResolveTagMember() }
                        delete("/override") { call.handleClearOverride() }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture helpers
    // -------------------------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "PlanChain IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('PC IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', 10) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun seedOverrideSource(projectId: Int, itemId: String, sourceKey: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_plan_override (project_id, item_id, source_key, tag_member)
                VALUES (?, ?, ?, NULL)
                ON CONFLICT (project_id, item_id)
                DO UPDATE SET source_key = EXCLUDED.source_key, tag_member = NULL, updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, sourceKey)
            }
        ).process(Unit)
    }

    private data class OverrideRow(val sourceKey: String?, val tagMember: String?)

    private fun getOverrideRow(projectId: Int, itemId: String): OverrideRow? = runBlocking {
        DatabaseSteps.query<Pair<Int, String>, OverrideRow?>(
            sql = SafeSQL.select(
                "SELECT source_key, tag_member FROM resource_gathering_plan_override WHERE project_id = ? AND item_id = ?"
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.first)
                stmt.setString(2, inp.second)
            },
            resultMapper = { rs ->
                if (rs.next()) OverrideRow(
                    sourceKey = rs.getString("source_key"),
                    tagMember = rs.getString("tag_member")
                ) else null
            }
        ).process(Pair(projectId, itemId)).getOrNull()
    }
}
