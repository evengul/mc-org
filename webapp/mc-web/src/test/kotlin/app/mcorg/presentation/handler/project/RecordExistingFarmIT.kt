package app.mcorg.presentation.handler.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.handleRecordExistingFarm
import app.mcorg.pipeline.resources.GetWorldFarmSuppliesStep
import app.mcorg.pipeline.resources.WorldFarmSuppliesInput
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Endpoint tests for "record an existing farm" (MCO-298):
 * - the project is created operational (COMPLETED/DONE) with its productions attached,
 *   and immediately counts as farm supply for other projects (MCO-296)
 * - location is optional, but partial coordinates are rejected
 * - validation failures (no name, no produced items, unknown item) create nothing
 * - non-admin world members are refused
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class RecordExistingFarmIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 98, 0)
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val bamboo = Item("minecraft:bamboo", "Bamboo")

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        val serverData = ServerData(
            version = version,
            items = listOf(ironIngot, bamboo),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/iron_ore.json",
                    producedItems = listOf(ironIngot to ResourceQuantity.ItemQuantity(1))
                )
            )
        )
        val stored = runBlocking { StoreMinecraftDataStep.process(serverData) }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld("RecordExistingFarm IT World")
    }

    // ---- success --------------------------------------------------------------

    @Test
    fun `recording a farm creates a done project with its productions and supplies other projects`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "farmName=Iron Farm&farmDescription=Behind the base&farmType=FARMING" +
                    "&farmX=120&farmY=64&farmZ=-40&farmDimension=OVERWORLD" +
                    "&productions[minecraft:iron_ingot]=400&productions[minecraft:bamboo]="
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("/worlds/$worldId/projects", response.headers["HX-Redirect"])

        val farm = readProject(worldId, "Iron Farm")!!
        assertEquals(ProjectStage.COMPLETED.name, farm.stage)
        assertEquals(ProjectState.DONE.name, farm.state)
        assertEquals("Behind the base", farm.description)
        assertEquals(listOf(120, 64, -40), listOf(farm.x, farm.y, farm.z))
        assertEquals("OVERWORLD", farm.dimension)
        assertEquals(
            listOf("minecraft:bamboo" to 0, "minecraft:iron_ingot" to 400),
            readProductions(farm.id),
            "a blank rate is recorded as unknown (0)"
        )

        // The whole point of recording it Done: it is world supply straight away.
        val supplies = runBlocking {
            GetWorldFarmSuppliesStep.process(WorldFarmSuppliesInput(worldId, excludeProjectId = -1))
        }
        assertIs<Result.Success<*>>(supplies)
        val suppliedItems = (supplies as Result.Success).value.map { it.itemId to it.projectName }
        assertTrue(suppliedItems.contains("minecraft:iron_ingot" to "Iron Farm"), "got $suppliedItems")

        deleteProject(farm.id)
    }

    /** Also covers the non-HTMX submit path, which redirects with a Location header. */
    @Test
    fun `location is optional`() = testApplication {
        setupRoutes()
        val noRedirects = createClient { followRedirects = false }

        val response = noRedirects.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Bamboo Farm&productions[minecraft:bamboo]=1200")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/worlds/$worldId/projects", response.headers["Location"])
        val farm = readProject(worldId, "Bamboo Farm")!!
        assertNull(farm.dimension, "no location means no dimension")
        assertEquals(listOf("minecraft:bamboo" to 1200), readProductions(farm.id))

        deleteProject(farm.id)
    }

    // ---- validation -----------------------------------------------------------

    @Test
    fun `a farm without produced items is rejected`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Empty Farm")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertNull(readProject(worldId, "Empty Farm"), "nothing is created when validation fails")
    }

    @Test
    fun `an unknown item is rejected`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Mystery Farm&productions[minecraft:not_a_real_item]=10")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertNull(readProject(worldId, "Mystery Farm"))
    }

    @Test
    fun `a blank name is rejected`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=&productions[minecraft:bamboo]=10")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `half a location is rejected`() = testApplication {
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Lopsided Farm&farmX=10&productions[minecraft:bamboo]=10")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertNull(readProject(worldId, "Lopsided Farm"))
    }

    // ---- auth -----------------------------------------------------------------

    @Test
    fun `a world member without admin role cannot record a farm`() = testApplication {
        setupRoutes()
        val member = createExtraUser("member-cannot-record-farm")
        addWorldMember(member.id, worldId, Role.MEMBER, "member-${member.id}")

        val response = client.post("/worlds/$worldId/projects/farm") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Member Farm&productions[minecraft:bamboo]=10")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(readProject(worldId, "Member Farm"))
    }

    @Test
    fun `unauthenticated requests redirect`() = testApplication {
        setupRoutes()
        val unauth = createClient { followRedirects = false }

        val response = unauth.post("/worlds/$worldId/projects/farm") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmName=Anon Farm&productions[minecraft:bamboo]=10")
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ---- routing — mirrors WorldHandler's /projects block -----------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    post("/farm") { call.handleRecordExistingFarm() }
                }
            }
        }
    }

    // ---- fixtures + read helpers ------------------------------------------------

    private data class ProjectRow(
        val id: Int,
        val stage: String,
        val state: String,
        val description: String,
        val x: Int?,
        val y: Int?,
        val z: Int?,
        val dimension: String?,
    )

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun readProject(worldId: Int, name: String): ProjectRow? = runBlocking {
        val result = DatabaseSteps.query<Unit, ProjectRow?>(
            sql = SafeSQL.select(
                "SELECT id, stage, state, description, location_x, location_y, location_z, location_dimension " +
                    "FROM projects WHERE world_id = ? AND name = ?"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setString(2, name)
            },
            resultMapper = { rs ->
                if (!rs.next()) null else ProjectRow(
                    id = rs.getInt("id"),
                    stage = rs.getString("stage"),
                    state = rs.getString("state"),
                    description = rs.getString("description") ?: "",
                    x = rs.getInt("location_x").takeIf { !rs.wasNull() },
                    y = rs.getInt("location_y").takeIf { !rs.wasNull() },
                    z = rs.getInt("location_z").takeIf { !rs.wasNull() },
                    dimension = rs.getString("location_dimension"),
                )
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun readProductions(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, rate_per_hour FROM project_productions WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("rate_per_hour"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun deleteProject(projectId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(projectId)
    }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role, displayName: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, worldId)
                stmt.setString(3, displayName)
                stmt.setInt(4, role.level)
            }
        ).process(Unit)
        CacheManager.onMemberAdded(userId, worldId)
        CacheManager.worldMemberRole.asMap().keys
            .filter { it.startsWith("$userId:$worldId:") }
            .forEach { CacheManager.worldMemberRole.invalidate(it) }
    }
}
