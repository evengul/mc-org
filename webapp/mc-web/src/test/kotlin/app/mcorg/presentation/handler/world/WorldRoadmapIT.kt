package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.handleGetWorldRoadmap
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
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
 * `GET /worlds/{worldId}/roadmap` (MCO-288): the derived dependency table, its empty state,
 * and the membership gate.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldRoadmapIT : WithUser() {

    companion object {
        /** The per-edge status labels the cells print (MCO-318), matched as element text. */
        private const val STATUS_BLOCKING = ">blocking<"
        private const val STATUS_SUPPLYING = ">supplying<"
    }

    @Test
    fun `an unfinished farm blocks, and both columns say so`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Roadmap IT World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val response = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "roadmap-table")

        // The IA asks the upstream cell to name both halves: the project and the resource.
        val consumerRow = roadmapRow(body, "Beacon Build")
        assertContains(consumerRow.dependsOn, "Iron Farm")
        assertContains(consumerRow.dependsOn, "Iron Ingot")
        assertContains(consumerRow.dependsOn, "/worlds/$worldId/projects/$farm")
        assertContains(consumerRow.dependsOn, STATUS_BLOCKING)

        // MCO-318: the same edge, read from the other end, must make the same claim.
        val farmRow = roadmapRow(body, "Iron Farm")
        assertContains(farmRow.supplies, "Beacon Build")
        assertContains(farmRow.supplies, "/worlds/$worldId/projects/$consumer")
        assertContains(farmRow.supplies, STATUS_BLOCKING)
        assertFalse(farmRow.dependsOn.contains("roadmap-edge"), "the farm depends on nothing")

        deleteWorld(worldId)
    }

    @Test
    fun `an operational farm supplies its consumer instead of blocking it`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Operational Roadmap World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        // The relationship is still on the roadmap — it just stopped being a blocker, which
        // the summary line reports (it only counts blocked projects when there are any).
        assertContains(body, "2 projects")
        assertFalse(body.contains("1 blocked"), "an operational farm blocks nobody")

        // MCO-318: the consumer's own row used to show "—" here while the farm's row claimed
        // to block it. Both cells now show the relationship, marked as supply, not blocking.
        val consumerRow = roadmapRow(body, "Beacon Build")
        assertContains(consumerRow.dependsOn, "Iron Farm")
        assertContains(consumerRow.dependsOn, "Iron Ingot")
        assertContains(consumerRow.dependsOn, STATUS_SUPPLYING)
        assertFalse(consumerRow.dependsOn.contains(STATUS_BLOCKING), "a DONE farm blocks nothing")

        val farmRow = roadmapRow(body, "Iron Farm")
        assertContains(farmRow.supplies, "Beacon Build")
        assertContains(farmRow.supplies, STATUS_SUPPLYING)
        assertFalse(farmRow.supplies.contains(STATUS_BLOCKING), "a DONE farm blocks nothing")

        deleteWorld(worldId)
    }

    /**
     * MCO-466 — the Forever world case: a witch farm that has run for months and a ghast farm
     * still being built both make gunpowder. Judged on its own the ghast farm looks like a
     * prerequisite, and the roadmap said the storage system was blocked for 5 gunpowder while
     * the witch farm supplied that same gunpowder one line above.
     */
    @Test
    fun `a planned farm does not block for an item an operational farm already supplies`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Two Producer World")
        val consumer = createProject(worldId, "Storage System")
        val running = createProject(worldId, "Witch Farm")
        val planned = createProject(worldId, "Ghast Farm")
        createRequirement(consumer, "minecraft:gunpowder", "Gunpowder")
        createDemand(consumer, "minecraft:gunpowder", "Gunpowder", 5)
        createProduction(running, "minecraft:gunpowder", "Gunpowder")
        createProduction(planned, "minecraft:gunpowder", "Gunpowder")
        runBlocking { UpdateProjectStageStep(running).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        // Both relationships stay on the roadmap — the ghast farm really will make gunpowder,
        // and MCO-318 needs both directions reading the same edge set. What changes is blocking.
        val consumerRow = roadmapRow(body, "Storage System")
        assertContains(consumerRow.dependsOn, "Witch Farm")
        assertContains(consumerRow.dependsOn, "Ghast Farm")
        assertFalse(
            consumerRow.dependsOn.contains(STATUS_BLOCKING),
            "nothing blocks: an operational farm already makes the gunpowder",
        )
        assertFalse(body.contains("blocked"), "the summary must not count this project as blocked")

        // Read from the other end, the planned farm must make the same claim.
        assertFalse(
            roadmapRow(body, "Ghast Farm").supplies.contains(STATUS_BLOCKING),
            "the producer's own row must agree it is not blocking",
        )

        deleteWorld(worldId)
    }

    /**
     * The other half of MCO-466: coverage is per item, so an unfinished farm still blocks for
     * whatever nothing operational makes. Without this the fix would silently unblock a world.
     */
    @Test
    fun `a planned farm still blocks for an item nothing operational makes`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Partial Coverage World")
        val consumer = createProject(worldId, "Storage System")
        val running = createProject(worldId, "Witch Farm")
        val planned = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:gunpowder", "Gunpowder")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:gunpowder", "Gunpowder", 5)
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(running, "minecraft:gunpowder", "Gunpowder")
        createProduction(planned, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(running).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        val consumerRow = roadmapRow(body, "Storage System")
        assertContains(consumerRow.dependsOn, STATUS_BLOCKING)
        assertContains(consumerRow.dependsOn, "Iron Farm")
        assertContains(body, "1 blocked")
        assertContains(roadmapRow(body, "Iron Farm").supplies, STATUS_BLOCKING)

        deleteWorld(worldId)
    }

    @Test
    fun `finished projects sort below the work that is left`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Roadmap Order World")
        // Named so that every tiebreak the old rule had — depth 0 first, then name — puts the
        // finished farm on top: it is layer 0 because nothing blocks it, and alphabetically first.
        val farm = createProject(worldId, "Alpha Farm")
        val consumer = createProject(worldId, "Zulu Build")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        // MCO-405: the dev world opened on 20-odd finished farms with the one active build
        // underneath. Depth is still the sequence — it just no longer leads.
        val buildAt = body.indexOf("Zulu Build")
        val farmAt = body.indexOf("Alpha Farm")
        assertTrue(buildAt in 1..<farmAt, "unfinished work comes first: $buildAt should precede $farmAt")

        deleteWorld(worldId)
    }

    @Test
    fun `a farm supplying a material the build never places still gets an edge`() = testApplication {
        // MCO-316's headline case, from the YAMS import. The build declares 5,630 hoppers and
        // places no literal gold nugget, so matching declared rows found nothing and the Gold
        // Farm — 7,299 units of real demand — produced no edge whatsoever. Matching derived
        // demand finds it.
        setupRoutes()
        val worldId = createWorld("Derived Demand World")
        val consumer = createProject(worldId, "YAMS")
        val farm = createProject(worldId, "Gold Farm")
        createRequirement(consumer, "minecraft:hopper", "Hopper")
        createDemand(consumer, "minecraft:gold_nugget", "Gold Nugget", 7299)
        createProduction(farm, "minecraft:gold_nugget", "Gold Nugget")

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        val consumerRow = roadmapRow(body, "YAMS")
        assertContains(consumerRow.dependsOn, "Gold Farm")
        assertContains(consumerRow.dependsOn, "Gold Nugget")

        deleteWorld(worldId)
    }

    @Test
    fun `the edge says how much of the demand the farm covers`() = testApplication {
        // "Cobblestone Generator — Cobblestone" next to a single decorative block was the
        // misleading half of the same bug: the farm covered the largest line of gathering work
        // in the project and the cell gave no way to tell.
        setupRoutes()
        val worldId = createWorld("Quantified Roadmap World")
        val consumer = createProject(worldId, "YAMS")
        val farm = createProject(worldId, "Cobblestone Generator")
        createRequirement(consumer, "minecraft:cobblestone", "Cobblestone")
        createDemand(consumer, "minecraft:cobblestone", "Cobblestone", 74564)
        createProduction(farm, "minecraft:cobblestone", "Cobblestone")

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        val consumerRow = roadmapRow(body, "YAMS")
        assertContains(consumerRow.dependsOn, "74,564 Cobblestone")

        deleteWorld(worldId)
    }

    @Test
    fun `a project with no derived demand contributes no farm edges`() = testApplication {
        // The honest consequence of matching derived demand: a project nobody has planned has
        // nothing to match. The roadmap tries to fill it in (see GetWorldRoadMapStep), which
        // needs an ingested graph these tests do not have — so here it stays empty rather than
        // inventing an edge from the declared row.
        setupRoutes()
        val worldId = createWorld("Unplanned Roadmap World")
        val consumer = createProject(worldId, "Unopened Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val body = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }.bodyAsText()

        val consumerRow = roadmapRow(body, "Unopened Build")
        assertFalse(consumerRow.dependsOn.contains("Iron Farm"))

        deleteWorld(worldId)
    }

    @Test
    fun `a world with no projects gets the empty state, not a bare table`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Empty Roadmap World")

        val response = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Nothing to sequence yet")
        assertContains(body, "/worlds/$worldId/projects")
        assertFalse(body.contains("roadmap-table"), "no table until there is something to sequence")

        deleteWorld(worldId)
    }

    @Test
    fun `a non-member cannot read the roadmap`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Private Roadmap World")
        val outsider = createExtraUser("roadmap-outsider")

        val response = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this, outsider) }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        deleteWorld(worldId)
    }

    @Test
    fun `unauthenticated requests redirect`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Anon Roadmap World")
        val unauth = createClient { followRedirects = false }

        val response = unauth.get("/worlds/$worldId/roadmap")

        assertEquals(HttpStatusCode.Found, response.status)

        deleteWorld(worldId)
    }

    /**
     * Opening a world lands on its roadmap (MCO-474).
     *
     * The status assertion is the point, not a formality: this used to be a **301**, which
     * browsers cache indefinitely, so the previous target outlives any change to it. A 302
     * keeps the next change to this route actually deliverable.
     */
    @Test
    fun `a world lands on its roadmap, and not permanently`() = testApplication {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get {
                    val id = call.parameters["worldId"]!!.toInt()
                    call.respondRedirect("/worlds/$id/roadmap", permanent = false)
                }
            }
        }
        val worldId = createWorld("Landing World")
        val noFollow = createClient { followRedirects = false }

        val response = noFollow.get("/worlds/$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds/$worldId/roadmap", response.headers["Location"])

        deleteWorld(worldId)
    }

    /** The tab pair is the way back out of the roadmap, on both of the world's pages. */
    @Test
    fun `the roadmap renders the world tabs with roadmap marked current`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Tabs Roadmap World")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "/worlds/$worldId/projects")
        assertContains(body, "world-tabs__tab--active")
        assertContains(body, "aria-current=\"page\"")

        deleteWorld(worldId)
    }

    /**
     * The roadmap is what a world opens to, so it carries the world's primary action too
     * (MCO-474) — it was previously the one view with no way to add anything.
     *
     * Asserting the dialogs, not just the trigger, is the point: every door calls `showModal()`
     * on a specific `<dialog>`, so a menu rendered without them gives you doors that silently
     * do nothing — and a test that only looked for the button would pass.
     */
    @Test
    fun `the roadmap offers the new project menu, with the dialogs its doors open`() = testApplication {
        setupRoutes()
        val worldId = createWorld("New Project Roadmap World")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "new-project-menu")
        assertContains(body, "+ New project")
        assertContains(body, "create-project-modal")
        assertContains(body, "schematic-project-modal")

        deleteWorld(worldId)
    }

    // ---- reading the rendered table -------------------------------------------------

    /** The two edge cells of one project's row, so an assertion can name which column it means. */
    private data class RoadmapRowCells(val dependsOn: String, val supplies: String)

    /**
     * Slices the row belonging to [projectName] out of the rendered table. Cells are found by
     * their `data-label` (which the mobile stacked-card layout needs anyway), so the assertions
     * read one column at a time instead of searching the whole page and hoping.
     */
    private fun roadmapRow(body: String, projectName: String): RoadmapRowCells {
        val row = body.split("data-label=\"Layer\"")
            .drop(1)
            .firstOrNull { it.substringBefore("data-label=\"State\"").contains(">$projectName<") }
            ?: error("no roadmap row for $projectName")
        return RoadmapRowCells(
            dependsOn = row.substringAfter("data-label=\"Depends on\"").substringBefore("data-label=\"Supplies\""),
            supplies = row.substringAfter("data-label=\"Supplies\"").substringBefore("</tr>"),
        )
    }

    // ---- routing — mirrors WorldHandler ------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get("/roadmap") { call.handleGetWorldRoadmap() }
            }
        }
    }

    // ---- fixtures ------------------------------------------------------------------

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

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createRequirement(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, 32)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    /**
     * Seeds derived plan demand directly (MCO-316).
     *
     * Farm edges match this rather than `resource_gathering`, and deriving it for real needs an
     * ingested item-source graph that these tests deliberately do not have. Seeding keeps the
     * roadmap's own logic under test instead of the engine's — the derivation itself is covered
     * where it lives.
     */
    private fun createDemand(projectId: Int, itemId: String, name: String, quantity: Long) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand
                    (project_id, item_id, item_name, quantity, activity_group, node_status)
                VALUES (?, ?, ?, ?, 'GATHER', 'RESOLVED')
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setLong(4, quantity)
            }
        ).process(Unit)
        // Marking it derived stops the roadmap trying to fill this project in.
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
}
