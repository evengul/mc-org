package app.mcorg.presentation.handler.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.pipeline.project.CreateProjectInput
import app.mcorg.pipeline.project.CreateProjectStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.settings.general.handleGetWorldVersionImpact
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldVersion
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
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
import kotlin.test.assertFalse

/**
 * MCO-157 — the Game Version selector and the preflight behind it.
 *
 * The fixture mirrors a removal that actually happened: `minecraft:grass` is in 1.20.2's catalog
 * and gone from 1.20.3's, where it came back as `minecraft:short_grass`. That is the whole reason
 * the preflight exists, so it is the case the tests are built on rather than an invented id.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class UpdateWorldVersionIT : WithUser() {

    @BeforeAll
    fun seedCatalog() {
        // 1.20.2 has grass and stone; 1.20.3 dropped grass for short_grass.
        insertVersion("1.20.2", listOf("minecraft:grass" to "Grass", "minecraft:stone" to "Stone"))
        insertVersion("1.20.3", listOf("minecraft:short_grass" to "Short Grass", "minecraft:stone" to "Stone"))
        // The tag survives both versions, so the override test isolates the pinned *member*.
        insertTag("1.20.2", "#minecraft:planks")
        insertTag("1.20.3", "#minecraft:planks")
        CacheManager.supportedVersions.invalidateAll()
    }

    @Test
    fun `an admin can switch the world to another ingested version`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Switch World")

        val response = client.patch("/worlds/$worldId/settings/version") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("version=1.20.3")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("1.20.3", storedVersion(worldId))
    }

    @Test
    fun `a version this instance has never ingested is rejected`() {
        // The bug this closes: the check used to be "does the string parse", so 1.99.0 passed and
        // left the world pointing at an empty item catalog — every plan blocked, nothing saying why.
        testApplication {
            setupRoutes()
            val worldId = createWorld("Version Uningested World")

            val response = client.patch("/worlds/$worldId/settings/version") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("version=${uningestedVersion()}")
            }

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals("1.20.2", storedVersion(worldId))
        }
    }

    @Test
    fun `a missing version parameter is rejected`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Missing World")

        val response = client.patch("/worlds/$worldId/settings/version") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        // 400, not 422: an absent parameter is a malformed request, while a well-formed request
        // naming a version we do not have is the unprocessable one above.
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("1.20.2", storedVersion(worldId))
    }

    @Test
    fun `an unauthenticated request is sent to sign-in rather than changing anything`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Auth World")

        val response = client.patch("/worlds/$worldId/settings/version") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("version=1.20.3")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("1.20.2", storedVersion(worldId))
    }

    @Test
    fun `the preflight names the item the target version drops, and the project holding it`() =
        testApplication {
            setupRoutes()
            val worldId = createWorld("Version Impact World")
            val projectId = createProject(worldId, "Cottage")
            addRequirement(projectId, "minecraft:grass", "Grass")
            addRequirement(projectId, "minecraft:stone", "Stone")

            val response = client.get("/worlds/$worldId/settings/version/impact?version=1.20.3") {
                addAuthCookie(this)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "Grass")
            assertContains(body, "Cottage")
            assertContains(body, "Needed")
            // Stone survives the version step, so naming it would be noise in a list of casualties.
            assertFalse(body.contains("Stone"), "expected only the dropped item to be listed")
        }

    @Test
    fun `the preflight is silent when nothing is lost`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Clean World")
        val projectId = createProject(worldId, "Stone Path")
        addRequirement(projectId, "minecraft:stone", "Stone")

        val response = client.get("/worlds/$worldId/settings/version/impact?version=1.20.3") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Nothing in this world is lost")
        assertContains(body, "Switch to 1.20.3")
    }

    @Test
    fun `the preflight counts a pinned tag member as well as a requirement`() = testApplication {
        // An override's stored ids are the quietest casualty of a version change: a pinned choice
        // that no longer resolves does not error, it just stops applying.
        setupRoutes()
        val worldId = createWorld("Version Override World")
        val projectId = createProject(worldId, "Barn")
        addTagMemberOverride(projectId, "#minecraft:planks", "minecraft:grass")

        val response = client.get("/worlds/$worldId/settings/version/impact?version=1.20.3") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "minecraft:grass")
        assertContains(body, "Pinned choice")
    }

    @Test
    fun `the preflight offers nothing for the version the world is already on`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Same World")
        val projectId = createProject(worldId, "Cottage")
        addRequirement(projectId, "minecraft:grass", "Grass")

        val response = client.get("/worlds/$worldId/settings/version/impact?version=1.20.2") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(
            response.bodyAsText().contains("Switch to"),
            "re-picking the current version should clear the preview, not offer a switch",
        )
    }

    @Test
    fun `the preflight rejects a version that is not ingested`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Impact Bad World")

        val response = client.get("/worlds/$worldId/settings/version/impact?version=${uningestedVersion()}") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    patch("/version") { call.handleUpdateWorldVersion() }
                    get("/version/impact") { call.handleGetWorldVersionImpact() }
                }
            }
        }
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = MinecraftVersion.fromString("1.20.2"))
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = CreateProjectStep(worldId).process(
            CreateProjectInput(name = name, description = "test", type = ProjectType.BUILDING)
        )
        (result as Result.Success).value
    }

    private fun addRequirement(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, 64)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            },
        ).process(Unit)
        Unit
    }

    private fun addTagMemberOverride(projectId: Int, tagId: String, memberItemId: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering_plan_override (project_id, item_id, tag_member) VALUES (?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, tagId)
                stmt.setString(3, memberItemId)
            },
        ).process(Unit)
        Unit
    }

    private fun insertVersion(version: String, items: List<Pair<String, String>>) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT DO NOTHING"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version) },
        ).process(Unit)

        items.forEach { (itemId, itemName) ->
            DatabaseSteps.update<Unit>(
                SafeSQL.insert(
                    """
                    INSERT INTO minecraft_items (version, item_id, item_name)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """.trimIndent()
                ),
                parameterSetter = { stmt, _ ->
                    stmt.setString(1, version)
                    stmt.setString(2, itemId)
                    stmt.setString(3, itemName)
                },
            ).process(Unit)
        }
        Unit
    }

    /**
     * A version that is provably not ingested *right now*, rather than a literal.
     *
     * Do not replace this with a hardcoded version. The database tier shares one PostgreSQL across
     * every test class, and `minecraft_version` is global reference data rather than world-scoped,
     * so any class can make a given version real for every other class — `GenerateGatheringPlanStepTest`
     * seeds `1.99.0` for its own graph, which is exactly what a hardcoded `1.99.0` here collided with.
     * Asking the same step the validator asks cannot drift from what the validator will see.
     */
    private fun uningestedVersion(): String = runBlocking {
        val supported = GetSupportedVersionsStep.getSupportedVersions().mapTo(mutableSetOf()) { it.toString() }
        generateSequence(90) { it + 1 }.map { "1.$it.0" }.first { it !in supported }
    }

    private fun insertTag(version: String, tag: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO minecraft_tag (version, tag, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, tag)
                stmt.setString(3, tag.removePrefix("#"))
            },
        ).process(Unit)
        Unit
    }

    private fun storedVersion(worldId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Int, String?>(
            SafeSQL.select("SELECT version FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> if (rs.next()) rs.getString("version") else null },
        ).process(worldId)
        (result as Result.Success).value!!
    }
}
