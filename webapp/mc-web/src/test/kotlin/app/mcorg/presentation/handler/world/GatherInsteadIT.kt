package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.prerequisiteFarmsFor
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
import app.mcorg.pipeline.world.roadmap.handleClearGatherInstead
import app.mcorg.pipeline.world.roadmap.handleGatherInstead
import app.mcorg.pipeline.world.roadmap.handleGetWorldRoadmap
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `gather instead ▸` on the roadmap's TO BUILD table (MCO-574): a farm still to build, taken out of
 * the plan without being cancelled, and put back.
 *
 * The hand-list half — its share moving from "promised" to "yours either way" — needs plans derived
 * against a game graph, and is in [RoadmapHandListSplitIT].
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GatherInsteadIT : WithUser() {

    /**
     * A storage build fed by a running cobblestone farm, and an iron farm not built yet. The iron farm
     * needs hoppers of its own, so it has demand — which is what would make it a final project once
     * nothing consumes from it any more.
     */
    private data class World(val id: Int, val storage: Int, val cobble: Int, val iron: Int)

    private fun world(name: String): World {
        val worldId = createWorld(name)
        val storage = createProject(worldId, "Storage System")
        val cobble = createProject(worldId, "Cobble Farm", state = "DONE")
        val iron = createProject(worldId, "Iron Farm")
        createDemand(storage, "minecraft:cobblestone", "Cobblestone", 51_575)
        createDemand(storage, "minecraft:iron_ingot", "Iron Ingot", 1_856)
        createDemand(iron, "minecraft:hopper", "Hopper", 5)
        createProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        createProduction(iron, "minecraft:iron_ingot", "Iron Ingot")
        return World(worldId, storage, cobble, iron)
    }

    // ---- the derivation ------------------------------------------------------------------------

    @Test
    fun `a farm gathered instead is nobody's prerequisite and not a node of the plan`() {
        val w = world("Derivation World")
        // A second farm still to build that the iron farm would wait on: the farm is out of the plan
        // in both directions, so this one must stop being upstream of anything because of it.
        val hopper = createProject(w.id, "Hopper Workshop")
        createProduction(hopper, "minecraft:hopper", "Hopper")
        assertTrue(roadmapOf(w.id).edges.any { it.fromNodeId == w.storage && it.toNodeId == w.iron })

        gatherInstead(w.iron)

        val roadmap = roadmapOf(w.id)
        assertTrue(roadmap.edges.none { w.iron in setOf(it.fromNodeId, it.toNodeId) }, "no edge in either direction")
        assertTrue(roadmap.nodes.none { it.projectId == w.iron }, "out of the plan")
        assertEquals(listOf(w.iron), roadmap.gatheringInstead.map { it.projectId }, "and listed as gathered instead")
        // The project page reads the same edges for its prerequisite line (MCO-461).
        assertTrue(runBlocking { prerequisiteFarmsFor(w.id, w.storage) }.none { it.projectId == w.iron })

        deleteWorld(w.id)
    }

    @Test
    fun `a farm reaching done clears the decision, and pausing it does not`() {
        val w = world("Trigger World")
        gatherInstead(w.iron)

        setState(w.iron, "PAUSED")
        assertTrue(isGatheredInstead(w.iron), "still a farm to build")

        setState(w.iron, "ACTIVE")
        setState(w.iron, "DONE")
        assertFalse(isGatheredInstead(w.iron), "a running farm supplies whatever was decided")

        deleteWorld(w.id)
    }

    // ---- the action ----------------------------------------------------------------------------

    @Test
    fun `gather instead takes the farm out of TO BUILD and lists it beneath`() = testApplication {
        setupRoutes()
        val w = world("Gather World")

        val before = page(w.id)
        val table = before.substringAfter("id=\"roadmap-to-build\"").substringBefore("rmg-section rmg-graph")
        assertContains(table, "Iron Farm")
        assertContains(table, "/worlds/${w.id}/roadmap/gather-instead")
        assertContains(table, "aria-label=\"Gather instead of building Iron Farm\"")

        val response = post(w.id, "/roadmap/gather-instead", w.iron)
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/worlds/${w.id}/roadmap", response.headers["Location"])

        val after = page(w.id)
        assertFalse(after.contains("id=\"roadmap-to-build\""), "nothing left to build")
        val section = after.substringAfter("GATHERING INSTEAD · 1").substringBefore("rmg-section rmg-")
        assertContains(section, "/worlds/${w.id}/projects/${w.iron}")
        assertContains(section, "/worlds/${w.id}/roadmap/gather-instead/clear")
        assertContains(section, "aria-label=\"Build Iron Farm after all\"")
        val panels = after.split("rmg-node--terminal").drop(1).map { it.substringBefore("</a>") }
        assertTrue(panels.isNotEmpty())
        assertTrue(panels.none { it.contains("Iron Farm") }, "its own hoppers do not make it a final project")
        assertFalse(after.contains("NOT IN ANY CHAIN"), "and it is not do-whenever either")

        deleteWorld(w.id)
    }

    @Test
    fun `build it after all puts the farm back`() = testApplication {
        setupRoutes()
        val w = world("Undo World")
        gatherInstead(w.iron)

        val response = post(w.id, "/roadmap/gather-instead/clear", w.iron)
        assertEquals(HttpStatusCode.SeeOther, response.status)

        assertFalse(isGatheredInstead(w.iron))
        val body = page(w.id)
        assertContains(body, "TO BUILD · 1 FARM")
        assertFalse(body.contains("GATHERING INSTEAD"))

        deleteWorld(w.id)
    }

    @Test
    fun `the action sits on every row of the ungrouped table too`() = testApplication {
        setupRoutes()
        val w = world("Ungrouped World")
        // Two states, so the table keeps its STATE column rather than grouping by destination.
        val gold = createProject(w.id, "Gold Farm", state = "PAUSED")
        createDemand(w.storage, "minecraft:gold_ingot", "Gold Ingot", 640)
        createProduction(gold, "minecraft:gold_ingot", "Gold Ingot")

        val table = page(w.id).substringAfter("id=\"roadmap-to-build\"").substringBefore("rmg-section rmg-graph")

        assertContains(table, "STATE")
        assertEquals(2, table.split("Gather instead of building").size - 1)

        deleteWorld(w.id)
    }

    // ---- what it refuses -----------------------------------------------------------------------

    @Test
    fun `a member cannot take a farm out of the world's plan, and is not offered to`() = testApplication {
        setupRoutes()
        val w = world("Member World")
        val member = createExtraUser()
        addWorldMember(member.id, w.id, Role.MEMBER)

        val response = createClient { followRedirects = false }.post("/worlds/${w.id}/roadmap/gather-instead") {
            addAuthCookie(this, member)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(Parameters.build { append("project", w.iron.toString()) }.formUrlEncode())
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(isGatheredInstead(w.iron))
        val body = client.get("/worlds/${w.id}/roadmap") { addAuthCookie(this, member) }.bodyAsText()
        assertContains(body, "Iron Farm")
        assertFalse(body.contains("Gather instead of building"))

        deleteWorld(w.id)
    }

    @Test
    fun `only a farm still to build in this world can be gathered instead`() = testApplication {
        setupRoutes()
        val w = world("Refusal World")
        val other = world("Someone Else's World")

        // Built: it supplies already, so there is nothing to gather instead of.
        assertEquals(HttpStatusCode.UnprocessableEntity, post(w.id, "/roadmap/gather-instead", w.cobble).status)
        // Makes nothing: not a farm.
        assertEquals(HttpStatusCode.UnprocessableEntity, post(w.id, "/roadmap/gather-instead", w.storage).status)
        // Another world's farm, through this world's route.
        assertEquals(HttpStatusCode.UnprocessableEntity, post(w.id, "/roadmap/gather-instead", other.iron).status)

        assertFalse(isGatheredInstead(w.cobble))
        assertFalse(isGatheredInstead(w.storage))
        assertFalse(isGatheredInstead(other.iron))

        deleteWorld(w.id)
        deleteWorld(other.id)
    }

    @Test
    fun `clearing through another world's route leaves the decision alone`() = testApplication {
        setupRoutes()
        val w = world("Mine World")
        val other = world("Their World")
        gatherInstead(other.iron)

        post(w.id, "/roadmap/gather-instead/clear", other.iron)

        assertTrue(isGatheredInstead(other.iron))

        deleteWorld(w.id)
        deleteWorld(other.id)
    }

    // ---- routing — mirrors WorldHandler ------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get("/roadmap") { call.handleGetWorldRoadmap() }
                route("/roadmap/gather-instead") {
                    install(WorldAdminPlugin)
                    post { call.handleGatherInstead() }
                    post("/clear") { call.handleClearGatherInstead() }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.page(worldId: Int): String =
        client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

    private suspend fun ApplicationTestBuilder.post(worldId: Int, path: String, projectId: Int): HttpResponse =
        createClient { followRedirects = false }.post("/worlds/$worldId$path") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(Parameters.build { append("project", projectId.toString()) }.formUrlEncode())
        }

    // ---- fixtures ------------------------------------------------------------------------------

    private fun roadmapOf(worldId: Int): Roadmap =
        (runBlocking { GetWorldRoadMapStep(worldId).process(Unit) } as Result.Success).value

    private fun gatherInstead(projectId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO project_gather_instead (project_id) VALUES (?)"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun isGatheredInstead(projectId: Int): Boolean = runBlocking {
        DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM project_gather_instead WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { it.next() }
        ).process(Unit).getOrNull()!!
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = MinecraftVersion.fromString("1.20.1"))
        )
        (result as Result.Success).value
    }

    private fun deleteWorld(worldId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(worldId)
    }

    private fun createProject(worldId: Int, name: String, state: String = "PENDING"): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    /** Seeded rather than derived, as [WorldRoadmapIT] does: there is no game graph here. */
    private fun createDemand(projectId: Int, itemId: String, name: String, quantity: Long) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand
                    (project_id, item_id, item_name, quantity, activity_group, node_status)
                VALUES (?, ?, ?, ?, 'GATHER', 'RAW_GATHER')
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setLong(4, quantity)
            }
        ).process(Unit)
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand_state (project_id, fingerprint)
                VALUES (?, 'seeded')
                ON CONFLICT (project_id) DO NOTHING
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun createProduction(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 0)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private fun setState(projectId: Int, state: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, worldId)
                stmt.setString(3, "member-$userId")
                stmt.setInt(4, role.level)
            }
        ).process(Unit)
    }
}
