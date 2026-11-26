package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.resources.handleGetPlanRow
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A plan's Collect section grouped by farm (MCO-604), and a farm's page saying what to set it to
 * (MCO-605), against real rows: Done farm projects with measured rates, and plans whose lines they
 * supply.
 *
 * The farms and quantities are Forever world's (MCO-603's fixture table), cut down to one line
 * per farm where the farm's other lines would add nothing.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FarmRuntimesIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 89, 0)

    private val items = listOf(
        "iron_nugget" to "Iron Nugget (Item)", "quartz" to "Nether Quartz (Item)", "bow" to "Bow (Item)",
        "wheat" to "Wheat (Item)", "bone_meal" to "Bone Meal (Item)", "prismarine_crystals" to "Prismarine Crystals (Item)",
        "gold_ingot" to "Gold Ingot (Item)", "cactus" to "Cactus (Block)", "honeycomb" to "Honeycomb (Item)",
        "oak_log" to "Oak Log (Block)", "spruce_log" to "Spruce Log (Block)",
    ).map { (id, name) -> Item("minecraft:$id", name) }

    private var worldId = 0
    private var storage = 0
    private var library = 0
    private var bartering = 0
    private var skeleton = 0
    private var cactusFarm = 0
    private var bees = 0
    private var tree = 0
    private var idleFarm = 0

    @BeforeAll
    fun setup() {
        val stored = runBlocking {
            StoreMinecraftDataStep.process(
                ServerData(
                    version = version,
                    items = items,
                    sources = items.map { item ->
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/${item.id.substringAfter(':')}.json",
                            producedItems = listOf(item to ResourceQuantity.ItemQuantity(1)),
                        )
                    },
                )
            )
        }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld()

        // Eight running farms, so Collect folds the short ones (more than six).
        bartering = farm("Bartering setup", "minecraft:iron_nugget" to 1_000, "minecraft:quartz" to 1_000)
        skeleton = farm("Skeleton spawner farm", "minecraft:bow" to 20)
        farm("Nanofarm", "minecraft:wheat" to 200)
        farm("Moss farm", "minecraft:bone_meal" to 200)
        farm("Guardian farm", "minecraft:prismarine_crystals" to 200)
        farm("Nether Fortress farm", "minecraft:gold_ingot" to 1_000)
        cactusFarm = farm("Cactus farm", "minecraft:cactus" to 1_000)
        bees = farm("Bee farm", "minecraft:honeycomb" to 0)

        tree = farm("Tree Farm")
        mode(tree, "Oak Mode", 0, "minecraft:oak_log" to 48_000)
        mode(tree, "Spruce Mode", 1, "minecraft:spruce_log" to 37_600)
        mode(tree, "Azalea Mode", 2, "minecraft:oak_log" to 38_100)

        idleFarm = farm("Idle farm", "minecraft:cactus" to 10)

        storage = project("Storage System")
        need(storage, "minecraft:iron_nugget", 296_800)
        need(storage, "minecraft:quartz", 7_498)
        need(storage, "minecraft:bow", 455)
        need(storage, "minecraft:wheat", 2_671)
        need(storage, "minecraft:bone_meal", 2_886)
        need(storage, "minecraft:prismarine_crystals", 1_815)
        need(storage, "minecraft:gold_ingot", 811)
        need(storage, "minecraft:cactus", 100)
        need(storage, "minecraft:honeycomb", 340)
        need(storage, "minecraft:spruce_log", 28_191)
        need(storage, "minecraft:oak_log", 57)

        library = project("Copper Library")
        need(library, "minecraft:spruce_log", 6_055)
    }

    @AfterAll
    fun teardown() = runBlocking {
        DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
        ).process(worldId)
        Unit
    }

    // ---- MCO-604: the plan -----------------------------------------------------

    @Test
    fun `Collect orders farms by the time they run, longest first`() = testApplication {
        setupRoutes()

        val body = page(storage)

        val order = listOf(bartering, skeleton, tree).map { body.indexOf("id=\"farm-run-$it\"") }
        assertTrue(order.all { it >= 0 }, "every farm has a block: $order")
        assertEquals(order.sorted(), order, "Bartering ~12.4 days, then the skeleton farm's 455 Bow at ~22.8 h, then Tree Farm ~45 min")
        assertContains(body, "~12.4 days")
        assertContains(body, "~22.8 h")
        assertContains(body, "Collect · longest run first")
    }

    @Test
    fun `the line a farm's time comes from is named in the heading, and is a row however small`() = testApplication {
        setupRoutes()

        val body = page(storage)

        assertContains(body, "set by Iron Nugget, 296,800 at 1,000/hr")
        // The heading is the one place that says it (decided 2026-11-26): no label on the row.
        assertFalse(body.contains("sets the time"))
        // 455 Bow is a small job by quantity, but it is what the skeleton farm runs for.
        assertContains(block(body, skeleton), "id=\"plan-activity-minecraft-bow\" data-item-name")
    }

    @Test
    fun `a farm with modes gets a heading per mode that takes real time`() = testApplication {
        setupRoutes()

        val treeBlock = block(page(storage), tree)

        assertContains(treeBlock, "2 of its 3 modes for this plan")
        assertContains(treeBlock, "Spruce Mode")
        assertContains(treeBlock, "~45 min")
        assertContains(treeBlock, "Oak Mode · under a minute")
    }

    @Test
    fun `farms under thirty minutes fold when there are more than six`() = testApplication {
        setupRoutes()

        val body = page(storage)

        val fold = body.substring(body.indexOf("class=\"collect-fold\""))
        assertContains(fold, "id=\"farm-run-$cactusFarm\"", message = "Cactus farm runs ~6 min of nine farms")
        assertContains(body, "1 more farm, under 30 min each")
    }

    @Test
    fun `a farm with no measured rate has no time, sorts last and never folds`() = testApplication {
        setupRoutes()

        val body = page(storage)

        val beeBlock = block(body, bees)
        assertContains(beeBlock, "Unknown time")
        assertContains(beeBlock, "Honeycomb rate is 0")
        assertContains(beeBlock, "Add a rate")
        assertContains(beeBlock, "work-row__left", message = "its line is a row, though by quantity it is a small job")
        val fold = body.indexOf("class=\"collect-fold\"")
        assertTrue(body.indexOf("id=\"farm-run-$bees\"") in body.indexOf("id=\"farm-run-$tree\"")..fold, "after the timed farms, before the fold")
    }

    @Test
    fun `logging a line swaps its farm's heading out of band`() = testApplication {
        setupRoutesWithProgress()
        page(storage)

        try {
            val response = client.patch("/worlds/$worldId/projects/$storage/plan/progress") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("itemId=minecraft:spruce_log&amount=2000&required=28191&working=true")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "id=\"farm-run-$tree-head\"", message = "Tree Farm's heading comes out of band")
            assertContains(body, "hx-swap-oob=\"true\"")
            // The figures themselves are FarmRuns' and pinned by its tests; this pins that they move.
            val heading = body.substring(body.indexOf("id=\"farm-run-$tree-head\""))
            assertContains(heading, "min left")
            assertContains(heading, "of ~")
            assertContains(body, "id=\"farm-run-$tree-mode-", message = "Spruce Mode's heading goes with it")
        } finally {
            // The other tests read this plan's figures, so the log must not outlive a failure.
            resetProgress(storage, "minecraft:spruce_log")
        }
    }

    @Test
    fun `a rate changed in a farm's panel brings the plan back out of band, so the panel stays open`() = testApplication {
        setupRoutes()

        val body = page(storage)
        // Collect listens for the production endpoints' announcement...
        assertContains(body, "hx-trigger=\"productionsChanged from:body\"")
        assertContains(body, "/worlds/$worldId/projects/$storage/detail-content?lens=list&amp;oob=true")

        // ...and the plan it fetches is marked out of band, which is what keeps the panel open.
        val refreshed = client.get("/worlds/$worldId/projects/$storage/detail-content?lens=list&oob=true") {
            addAuthCookie(this)
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        assertContains(refreshed.bodyAsText(), """id="project-content" hx-swap-oob="true" data-out-of-band="true"""")

        val plain = client.get("/worlds/$worldId/projects/$storage/detail-content?lens=list") { addAuthCookie(this) }
        assertFalse(plain.bodyAsText().contains("data-out-of-band"), "a view switch still replaces the plan")
    }

    @Test
    fun `logging a farm's last line makes its heading say done, not that its rate is 0`() = testApplication {
        setupRoutesWithProgress()
        page(storage)

        try {
            // Bow is the skeleton farm's only line, so ticking it finishes the farm.
            val response = client.patch("/worlds/$worldId/projects/$storage/plan/progress") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("itemId=minecraft:bow&amount=455&required=455")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "id=\"farm-run-$skeleton-head\"", message = "the farm's heading comes out of band")
            val heading = body.substring(body.indexOf("id=\"farm-run-$skeleton-head\""))
            assertContains(heading, "Done")
            assertFalse(heading.contains("rate is 0"), "a finished farm is not an unmeasured one")
            assertFalse(heading.contains("Add a rate"))
        } finally {
            resetProgress(storage, "minecraft:bow")
        }
    }

    @Test
    fun `reopening a fully logged farm line keeps it in its farm's block`() = testApplication {
        setupRoutesWithProgress()
        try {
            // Bow is a target, so logging all of it takes it out of the plan.
            client.patch("/worlds/$worldId/projects/$storage/plan/progress") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("itemId=minecraft:bow&amount=455&required=455")
            }

            val row = client.get("/worlds/$worldId/projects/$storage/plan/row/minecraft%3Abow?working=false") {
                addAuthCookie(this)
            }

            assertEquals(HttpStatusCode.OK, row.status)
            // The block's heading names the farm, so the row keeps only the switch, as on the page.
            assertFalse(row.bodyAsText().contains("work-row__method"), "the row names no source of its own")
        } finally {
            resetProgress(storage, "minecraft:bow")
        }
    }

    @Test
    fun `a manual target logged in full is not counted into a farm that also makes it`() = testApplication {
        setupRoutesWithProgress()
        setSource(storage, "minecraft:cactus", "manual")
        try {
            val response = client.patch("/worlds/$worldId/projects/$storage/plan/progress") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("itemId=minecraft:cactus&amount=100&required=100")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(
                response.bodyAsText().contains("id=\"farm-run-$cactusFarm-head\""),
                "the plan gathers cactus by hand, so the cactus farm's heading has nothing to do with this line",
            )
        } finally {
            setSource(storage, "minecraft:cactus", null)
            resetProgress(storage, "minecraft:cactus")
        }
    }

    @Test
    @Disabled("MCO-629")
    fun `what is logged against a target line is subtracted once`() = testApplication {
        setupRoutes()
        seedProgress(storage, "minecraft:honeycomb", 100)
        try {
            val beeBlock = block(page(storage), bees)

            val left = Regex("work-row__left\">([\\d,]+)<").find(beeBlock)?.groupValues?.get(1)
            assertEquals("240", left, "340 needed, 100 logged")
        } finally {
            resetProgress(storage, "minecraft:honeycomb")
        }
    }

    // ---- MCO-605: the farm page ------------------------------------------------

    @Test
    fun `a farm's page sums what every plan in the world asks of each mode`() = testApplication {
        setupRoutes()
        // Each plan's demand is cached when its page derives it; the farm page reads that cache.
        page(storage)
        page(library)

        val body = page(tree)

        assertContains(body, "Set it to · FarmRuntimes IT World")
        assertContains(body, "~55 min", message = "34,246 Spruce Log at 37,600/hr")
        assertContains(body, "34,246 Spruce Log")
        assertContains(body, "Storage System</a> 28,191")
        assertContains(body, "Copper Library</a> 6,055")
        assertContains(body, "Every mode · 3")
        assertContains(body, "not asked for: Oak Mode is faster")
    }

    @Test
    fun `a farm no plan asks anything of says nothing about it`() = testApplication {
        setupRoutes()
        page(storage)

        val body = page(idleFarm)

        assertFalse(body.contains("set-it-to"), "Cactus comes from the faster Cactus farm, so nobody asks the idle one")
    }

    // ---- helpers ---------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.page(projectId: Int): String {
        val response = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    /** One farm's block, up to the next block. */
    private fun block(body: String, farmId: Int): String {
        val start = body.indexOf("id=\"farm-run-$farmId\"")
        assertTrue(start >= 0, "no block for farm $farmId")
        val next = body.indexOf("class=\"collect-block\"", start + 1).takeIf { it > 0 } ?: body.length
        return body.substring(start, next)
    }

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get { call.handleGetProject() }
                    get("/detail-content") { call.handleGetDetailContent() }
                }
            }
        }
    }

    private fun ApplicationTestBuilder.setupRoutesWithProgress() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get { call.handleGetProject() }
                    patch("/plan/progress") { call.handleUpdatePlanProgress() }
                    get("/plan/row/{itemId}") { call.handleGetPlanRow() }
                }
            }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = "FarmRuntimes IT World", description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun project(name: String): Int = insertProject(name, "BUILDING", "PLANNING", "ACTIVE")

    /** A running farm, with one rate per item and no modes. */
    private fun farm(name: String, vararg rates: Pair<String, Int>): Int {
        val id = insertProject(name, "FARMING", "COMPLETED", "DONE")
        rates.forEach { (itemId, rate) -> produce(id, null, itemId, rate) }
        return id
    }

    private fun insertProject(name: String, type: String, stage: String, state: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', ?, ?, ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, type)
                stmt.setString(4, stage)
                stmt.setString(5, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun mode(projectId: Int, name: String, position: Int, rate: Pair<String, Int>) {
        val modeId = runBlocking {
            val result = DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO project_production_modes (project_id, name, position) VALUES (?, ?, ?) RETURNING id"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, projectId)
                    stmt.setString(2, name)
                    stmt.setInt(3, position)
                }
            ).process(Unit)
            (result as Result.Success).value
        }
        produce(projectId, modeId, rate.first, rate.second)
    }

    private fun produce(projectId: Int, modeId: Int?, itemId: String, ratePerHour: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_productions (project_id, mode_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                if (modeId == null) stmt.setNull(2, java.sql.Types.INTEGER) else stmt.setInt(2, modeId)
                stmt.setString(3, itemId)
                stmt.setString(4, items.first { it.id == itemId }.name)
                stmt.setInt(5, ratePerHour)
            }
        ).process(Unit)
    }

    private fun need(projectId: Int, itemId: String, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, items.first { it.id == itemId }.name)
                stmt.setInt(4, required)
            }
        ).process(Unit)
    }

    private fun seedProgress(projectId: Int, itemId: String, collected: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setInt(3, collected)
            }
        ).process(Unit)
    }

    private fun setSource(projectId: Int, itemId: String, sourceType: String?) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.update("UPDATE resource_gathering SET source_type = ? WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                if (sourceType == null) stmt.setNull(1, java.sql.Types.VARCHAR) else stmt.setString(1, sourceType)
                stmt.setInt(2, projectId)
                stmt.setString(3, itemId)
            }
        ).process(Unit)
    }

    private fun resetProgress(projectId: Int, itemId: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.delete("DELETE FROM resource_gathering_progress WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
            }
        ).process(Unit)
    }
}
