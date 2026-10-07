package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /worlds/{worldId}/roadmap` (MCO-288): the derived edges behind it, its empty state,
 * and the membership gate.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldRoadmapIT : WithUser() {

    // ---- the derived edges ----------------------------------------------------------
    // These used to be read off the Table view (`?view=table`), which printed every edge from both
    // ends. The view is gone (MCO-529); what they protect is the derivation, so they read
    // [GetWorldRoadMapStep] directly, against the same database.

    private fun roadmapOf(worldId: Int): Roadmap {
        val result = runBlocking { GetWorldRoadMapStep(worldId).process(Unit) }
        return (result as Result.Success).value
    }

    private fun Roadmap.edge(consumer: Int, producer: Int) =
        edges.singleOrNull { it.fromNodeId == consumer && it.toNodeId == producer }

    private fun Roadmap.node(projectId: Int) = nodes.single { it.projectId == projectId }

    @Test
    fun `an unfinished farm blocks its consumer, naming the item it is waited on for`() {
        val worldId = createWorld("Roadmap IT World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val roadmap = roadmapOf(worldId)

        val edge = assertNotNull(roadmap.edge(consumer, farm))
        assertEquals("Iron Ingot", edge.itemName)
        assertTrue(edge.isBlocking)
        // MCO-318: the same edge, read from either end, makes the same claim.
        assertEquals(listOf(farm), roadmap.node(consumer).blockingProjectIds)
        assertEquals(listOf(consumer), roadmap.node(farm).dependentProjectIds)
        assertTrue(roadmap.edges.none { it.fromNodeId == farm }, "the farm depends on nothing")

        deleteWorld(worldId)
    }

    @Test
    fun `an operational farm supplies its consumer instead of blocking it`() {
        val worldId = createWorld("Operational Roadmap World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createDemand(consumer, "minecraft:iron_ingot", "Iron Ingot", 32)
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }

        val roadmap = roadmapOf(worldId)

        // The relationship stays; it just stopped being a blocker.
        val edge = assertNotNull(roadmap.edge(consumer, farm))
        assertFalse(edge.isBlocking, "a DONE farm blocks nothing")
        assertFalse(roadmap.node(consumer).isBlocked)
        assertEquals(0, roadmap.getStatistics().blockedProjects)

        deleteWorld(worldId)
    }

    /**
     * MCO-466 — the Forever world case: a witch farm that has run for months and a ghast farm
     * still being built both make gunpowder. Judged on its own the ghast farm looks like a
     * prerequisite, and the roadmap said the storage system was blocked for 5 gunpowder while
     * the witch farm supplied that same gunpowder one line above.
     */
    @Test
    fun `a planned farm does not block for an item an operational farm already supplies`() {
        val worldId = createWorld("Two Producer World")
        val consumer = createProject(worldId, "Storage System")
        val running = createProject(worldId, "Witch Farm")
        val planned = createProject(worldId, "Ghast Farm")
        createRequirement(consumer, "minecraft:gunpowder", "Gunpowder")
        createDemand(consumer, "minecraft:gunpowder", "Gunpowder", 5)
        createProduction(running, "minecraft:gunpowder", "Gunpowder")
        createProduction(planned, "minecraft:gunpowder", "Gunpowder")
        runBlocking { UpdateProjectStageStep(running).process(ProjectStage.COMPLETED) }

        val roadmap = roadmapOf(worldId)

        // Both relationships stay — the ghast farm really will make gunpowder. What changes is blocking.
        assertNotNull(roadmap.edge(consumer, running))
        val pending = assertNotNull(roadmap.edge(consumer, planned))
        assertFalse(pending.isBlocking, "an operational farm already makes the gunpowder")
        assertFalse(roadmap.node(consumer).isBlocked)

        deleteWorld(worldId)
    }

    /**
     * The other half of MCO-466: coverage is per item, so an unfinished farm still blocks for
     * whatever nothing operational makes. Without this the fix would silently unblock a world.
     */
    @Test
    fun `a planned farm still blocks for an item nothing operational makes`() {
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

        val roadmap = roadmapOf(worldId)

        assertTrue(assertNotNull(roadmap.edge(consumer, planned)).isBlocking)
        assertEquals(listOf(planned), roadmap.node(consumer).blockingProjectIds)
        assertEquals(1, roadmap.getStatistics().blockedProjects)

        deleteWorld(worldId)
    }

    @Test
    fun `a farm supplying a material the build never places still gets an edge`() {
        // MCO-316's headline case, from the YAMS import. The build declares 5,630 hoppers and
        // places no literal gold nugget, so matching declared rows found nothing and the Gold
        // Farm — 7,299 units of real demand — produced no edge whatsoever. Matching derived
        // demand finds it.
        val worldId = createWorld("Derived Demand World")
        val consumer = createProject(worldId, "YAMS")
        val farm = createProject(worldId, "Gold Farm")
        createRequirement(consumer, "minecraft:hopper", "Hopper")
        createDemand(consumer, "minecraft:gold_nugget", "Gold Nugget", 7299)
        createProduction(farm, "minecraft:gold_nugget", "Gold Nugget")

        assertEquals("Gold Nugget", roadmapOf(worldId).edge(consumer, farm)?.itemName)

        deleteWorld(worldId)
    }

    @Test
    fun `the edge says how much of the demand the farm covers`() {
        // "Cobblestone Generator — Cobblestone" next to a single decorative block was the
        // misleading half of the same bug: the farm covered the largest line of gathering work
        // in the project and the edge gave no way to tell.
        val worldId = createWorld("Quantified Roadmap World")
        val consumer = createProject(worldId, "YAMS")
        val farm = createProject(worldId, "Cobblestone Generator")
        createRequirement(consumer, "minecraft:cobblestone", "Cobblestone")
        createDemand(consumer, "minecraft:cobblestone", "Cobblestone", 74564)
        createProduction(farm, "minecraft:cobblestone", "Cobblestone")

        assertEquals(74_564L, roadmapOf(worldId).edge(consumer, farm)?.quantity)

        deleteWorld(worldId)
    }

    @Test
    fun `a project with no derived demand contributes no farm edges`() {
        // The honest consequence of matching derived demand: a project nobody has planned has
        // nothing to match. The roadmap tries to fill it in (see GetWorldRoadMapStep), which
        // needs an ingested graph these tests do not have — so here it stays empty rather than
        // inventing an edge from the declared row.
        val worldId = createWorld("Unplanned Roadmap World")
        val consumer = createProject(worldId, "Unopened Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        assertNull(roadmapOf(worldId).edge(consumer, farm))

        deleteWorld(worldId)
    }

    // ---- the page -------------------------------------------------------------------

    @Test
    fun `an old link to the table view lands on the roadmap`() = testApplication {
        // `?view=table` was bookmarkable for a year. It is gone (MCO-529), and a bookmark should
        // land on the one roadmap there is rather than on an error.
        setupRoutes()
        val worldId = createWorld("Old Bookmark World")
        createProject(worldId, "Some Build")

        val response = client.get("/worlds/$worldId/roadmap?view=table") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "roadmap-title__name")
        assertFalse(body.contains("roadmap-table"), "there is no table view to render")
        assertFalse(body.contains("rmg-viewswitch"), "and nothing to switch between")

        deleteWorld(worldId)
    }

    @Test
    fun `final projects past the three panels are listed with links, not sent to another view`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Many Finals World")
        val farm = createProject(worldId, "Cobble Farm")
        createProduction(farm, "minecraft:cobblestone", "Cobblestone")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }
        val builds = (1..4).map { n ->
            createProject(worldId, "Build $n").also { createDemand(it, "minecraft:cobblestone", "Cobblestone", 1_000L * n) }
        }

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "+1 more final project")
        val more = body.substringAfter("MORE FINAL PROJECTS · 1", missingDelimiterValue = "")
        // Ranked by supply, so the smallest build is the one past the cap.
        assertContains(more, "/worlds/$worldId/projects/${builds.first()}")
        assertFalse(body.contains("table view"), "there is no other view to send anyone to")

        deleteWorld(worldId)
    }

    @Test
    fun `the graph view of a world with no projects gets the same empty state, not an empty card`() =
        testApplication {
            // The graph is the default view, so this is the first page a new world shows. It used
            // to render `.rmg-card` with four empty sections inside it.
            setupRoutes()
            val worldId = createWorld("Empty Graph Roadmap World")

            val response = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertEmptyWorldState(body)
            assertFalse(body.contains("rmg-card"), "no graph card until there is something to draw")

            deleteWorld(worldId)
        }

    @Test
    fun `the roadmap heads an empty world, as the projects tab does`() = testApplication {
        // The Projects tab titles itself whether or not it has projects. A Roadmap tab that
        // dropped its heading when empty made the two tabs read as different kinds of page.
        setupRoutes()
        val worldId = createWorld("Headed Empty World")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "roadmap-title__name", message = "no heading on an empty world")
        assertContains(body, "Headed Empty World", message = "the meta line names the world")
        assertContains(body, "0 projects")
        assertFalse(body.contains("0 layers"), "nothing to measure, so no measurement")

        deleteWorld(worldId)
    }

    /** The world's one empty state (`worldEmptyState`): the same three doors the project list offers. */
    private fun assertEmptyWorldState(body: String) {
        assertContains(body, "projects-empty-state")
        assertContains(body, "Plan your own project")
        assertContains(body, "record-farm-modal")
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
     *
     * The world needs a project for the *menu* half: an empty world hides it, because
     * `worldEmptyState` already offers the same doors (see the empty-state tests above).
     */
    @Test
    fun `the roadmap offers the new project menu, with the dialogs its doors open`() = testApplication {
        setupRoutes()
        val worldId = createWorld("New Project Roadmap World")
        createProject(worldId, "Something To Sequence")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "new-project-menu")
        assertContains(body, "+ New project")
        assertContains(body, "create-project-modal")
        assertContains(body, "schematic-project-modal")

        deleteWorld(worldId)
    }

    /**
     * The creation dialogs were written for the Projects tab and targeted `#projects-view`,
     * which only that tab has. htmx refuses to send a request whose target is missing, so
     * "Blank project" and "Record an existing farm" did nothing at all on the roadmap: no
     * request, no error, just `htmx:targetError` in the console. Both the populated world
     * (the menu) and the empty one (the empty-state cards) open the same dialogs.
     */
    @Test
    fun `every id an htmx form on the roadmap targets is on the roadmap`() = testApplication {
        setupRoutes()
        val emptyWorldId = createWorld("Targets Empty Roadmap World")
        val worldId = createWorld("Targets Roadmap World")
        createProject(worldId, "Something To Target")

        for (id in listOf(emptyWorldId, worldId)) {
            val body = client.get("/worlds/$id/roadmap") { addAuthCookie(this) }.bodyAsText()

            val targets = Regex("""hx-target(?:-error)?="#([\w-]+)"""").findAll(body).map { it.groupValues[1] }.toSet()
            val missing = targets.filterNot { body.contains("""id="$it"""") }
            assertEquals(emptyList(), missing, "world $id: hx-target ids with no element on the page")
        }

        deleteWorld(emptyWorldId)
        deleteWorld(worldId)
    }

    // ---- states the graph view has to name (found with MCO-563's fixture worlds) -----

    /** Fixture 4: decommissioned farms vanished from the graph view while the hand list tripled. */
    @Test
    fun `a decommissioned farm is listed as stopped`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Stopped Farm World")
        val storage = createProject(worldId, "Storage System")
        val farm = createProject(worldId, "Old Cobble Farm")
        createDemand(storage, "minecraft:cobblestone", "Cobblestone", 51_527, status = "RAW_GATHER")
        createProduction(farm, "minecraft:cobblestone", "Cobblestone")
        setState(farm, "DECOMMISSIONED")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "STOPPED · 1")
        val stopped = body.substringAfter("STOPPED · 1")
        assertContains(stopped, "Old Cobble Farm")
        // What it costs is a hand-list difference (MCO-572), which needs plans this database has no
        // game data to derive. Unmeasured is no number — never the zero that would mean "costs
        // nothing". RoadmapHandListSplitIT measures it against a real graph.
        assertFalse(stopped.contains("nothing it made is missing"), "unmeasured is not free")
        assertFalse(stopped.contains("no other farm covers"))

        deleteWorld(worldId)
    }

    /** Fixture 5: the graph drew an assumed order as fact; the question was table-view only. */
    @Test
    fun `the roadmap asks which of two farms comes first`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Cycle Graph World")
        val cobble = createProject(worldId, "Cobble Farm")
        val iron = createProject(worldId, "Iron Farm")
        createDemand(cobble, "minecraft:iron_ingot", "Iron Ingot", 1_856)
        createDemand(iron, "minecraft:cobblestone", "Cobblestone", 3_787)
        createProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        createProduction(iron, "minecraft:iron_ingot", "Iron Ingot")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "rmg-card", message = "this is the graph view")
        assertContains(body, "each supply the other. Which comes first?")
        assertContains(body, "/worlds/$worldId/roadmap/cycle-order")

        deleteWorld(worldId)
    }

    /** Fixture 3b: a build that is nothing but hand work was a "do them whenever" chip. */
    @Test
    fun `a build nothing but hand work gets a final project panel, not a do-whenever chip`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Hand Only World")
        val storage = createProject(worldId, "Storage System")
        val farm = createProject(worldId, "Cobble Farm")
        val castle = createProject(worldId, "Deepslate Castle")
        createDemand(storage, "minecraft:cobblestone", "Cobblestone", 51_575, status = "SUPPLIED")
        createProduction(farm, "minecraft:cobblestone", "Cobblestone")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }
        createDemand(castle, "minecraft:cobbled_deepslate", "Cobbled Deepslate", 12_000, status = "RAW_GATHER")

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        val panels = body.split("rmg-node--terminal").drop(1).map { it.substringBefore("</a>") }
        assertTrue(panels.any { it.contains("Deepslate Castle") }, "the castle is somewhere the world is heading")
        assertFalse(body.contains("NOT IN ANY CHAIN"), "and it is not also listed as do-whenever")

        deleteWorld(worldId)
    }

    /** MCO-571, frame 4B: once a farm produces, an unbuilt one is a TO BUILD row, not a band node. */
    @Test
    fun `with a farm producing, an unbuilt farm is listed to build and the graph draws no band`() = testApplication {
        setupRoutes()
        val worldId = createWorld("To Build World")
        val storage = createProject(worldId, "Storage System")
        val cobble = createProject(worldId, "Cobble Farm")
        val iron = createProject(worldId, "Iron Farm")
        createDemand(storage, "minecraft:cobblestone", "Cobblestone", 51_575)
        createDemand(storage, "minecraft:iron_ingot", "Iron Ingot", 1_856)
        createProduction(cobble, "minecraft:cobblestone", "Cobblestone")
        createProduction(iron, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(cobble).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        val table = body.substringAfter("id=\"roadmap-to-build\"", missingDelimiterValue = "")
            .substringBefore("rmg-section rmg-graph")
        // One farm, one state: grouped by where its output goes (frame 5A).
        assertContains(table, "TO BUILD · 1 FARM · ALL PLANNED · ANY ORDER")
        assertContains(table, "/worlds/$worldId/projects/$iron")
        assertContains(table, "FEEDS STORAGE SYSTEM ONLY · 1")
        assertContains(table, "1,856 Iron Ingot")
        assertFalse(table.contains("Cobble Farm"), "a producing farm is supply, not work")

        assertFalse(body.contains("rmg-node--start") || body.contains("rmg-node--sequence"), "no band in the graph")
        assertContains(body, "rmg-node--promised", message = "its supply arrives through the promised tab")
        assertContains(body, "href=\"#roadmap-to-build\"")
        assertFalse(body.contains("START HERE"), "one farm to start would rank a set")

        deleteWorld(worldId)
    }

    /** MCO-544, frame 5A: nothing produces yet, so the panels stand as a row and no band is drawn. */
    @Test
    fun `with nothing producing, the page keeps its sections and draws the panels without a column`() =
        testApplication {
            setupRoutes()
            val worldId = createWorld("Nothing Built World")
            val storage = createProject(worldId, "Storage System")
            val cobble = createProject(worldId, "Cobble Farm")
            val iron = createProject(worldId, "Iron Farm")
            createDemand(storage, "minecraft:cobblestone", "Cobblestone", 51_575)
            createDemand(storage, "minecraft:iron_ingot", "Iron Ingot", 1_856)
            createProduction(cobble, "minecraft:cobblestone", "Cobblestone")
            createProduction(iron, "minecraft:iron_ingot", "Iron Ingot")

            val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

            assertContains(body, "0 producing farms")
            assertContains(body, "NOTHING STARTED")
            assertContains(body, "TO BUILD · 2 FARMS · ALL PLANNED · ANY ORDER")
            assertContains(body, "FEEDS STORAGE SYSTEM ONLY · 2")
            assertContains(body, "SUPPLY · NO FARM PRODUCES YET")
            assertContains(body, "rmg-panelrow__card")
            assertContains(body, "not started · 2 of the 2 feed it")
            assertFalse(body.contains("from 0 farms"), "a panel no farm produces for says so in words")
            assertFalse(body.contains("class=\"rmg-panel\""), "no column, so no absolutely-positioned graph")
            assertFalse(body.contains("START HERE"), "two unordered farms have no first")

            deleteWorld(worldId)
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
    private fun createDemand(
        projectId: Int,
        itemId: String,
        name: String,
        quantity: Long,
        status: String = "RESOLVED",
    ) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO project_demand
                    (project_id, item_id, item_name, quantity, activity_group, node_status)
                VALUES (?, ?, ?, ?, 'GATHER', ?)
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setLong(4, quantity)
                stmt.setString(5, status)
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

    /** Sets a lifecycle state directly: DECOMMISSIONED has no stage that implies it. */
    private fun setState(projectId: Int, state: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }
}
