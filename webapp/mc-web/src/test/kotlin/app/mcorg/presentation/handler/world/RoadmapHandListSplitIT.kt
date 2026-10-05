package app.mcorg.presentation.handler.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
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
import io.ktor.server.routing.get
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * MCO-572 — the roadmap's promised / yours-either-way split and the STOPPED section's cost, on a
 * rendered page with a real (small) game graph behind it, so every number is a planner's.
 *
 * Storage needs ten furnaces (80 cobblestone), 20 sand and 16 oak logs. A running log farm supplies
 * the logs, so 100 is left by hand: 80 of it is what the unbuilt cobblestone farm promises, 20 is
 * yours either way.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class RoadmapHandListSplitIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 96, 0)

    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val furnace = Item("minecraft:furnace", "Furnace")
    private val sand = Item("minecraft:sand", "Sand")
    private val log = Item("minecraft:oak_log", "Oak Log")

    @BeforeAll
    fun setup() {
        CacheManager.invalidateAll()
        val serverData = ServerData(
            version = version,
            items = listOf(cobblestone, furnace, sand, log),
            sources = listOf(
                block("blocks/cobblestone.json", cobblestone),
                block("blocks/sand.json", sand),
                block("blocks/oak_log.json", log),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "furnace.json",
                    requiredItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(furnace to ResourceQuantity.ItemQuantity(1)),
                ),
            ),
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })
    }

    private fun block(file: String, item: Item) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.BLOCK,
        filename = file,
        producedItems = listOf(item to ResourceQuantity.ItemQuantity(1)),
    )

    /** The shared world: a storage build, a running log farm, an unbuilt cobblestone farm. */
    private fun world(name: String): Pair<Int, Int> {
        val worldId = createWorld(name)
        val storage = createProject(worldId, "Storage System", state = "ACTIVE")
        gather(storage, furnace, 10)
        gather(storage, sand, 20)
        gather(storage, log, 16)
        createProject(worldId, "Log Farm", state = "DONE").also { produce(it, log) }
        createProject(worldId, "Cobble Farm").also { produce(it, cobblestone) }
        return worldId to storage
    }

    @Test
    fun `a final project's hand list splits into what the farms being built promise and what stays yours`() =
        testApplication {
            setupRoutes()
            val (worldId, _) = world("Split World")

            val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

            val panel = body.substringAfter("FINAL PROJECT · 1 OF 1").substringBefore("</a>")
            assertContains(panel, "by hand now")
            assertContains(panel, ">100<")
            assertContains(panel, "promised")
            assertContains(panel, ">80<")
            assertContains(panel, "yours either way")
            assertContains(panel, ">20<")
            assertContains(body, "⚠ 80% of it is promised")
            assertContains(body, "80 disappears once the farm you're building is finished")

            deleteWorld(worldId)
        }

    @Test
    fun `a stopped farm costs what its running again would take off the hand list`() = testApplication {
        setupRoutes()
        val (worldId, _) = world("Stopped Sand World")
        createProject(worldId, "Old Sand Farm", state = "DECOMMISSIONED").also { produce(it, sand) }

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        // With the cobblestone farm built, 20 sand is all that is left; the sand farm running again
        // would take all of it.
        val stopped = body.substringAfter("STOPPED · 1")
        assertContains(stopped, "Old Sand Farm")
        assertContains(stopped, "⚠ 20 items no other farm covers, built or planned")

        deleteWorld(worldId)
    }

    @Test
    fun `a stopped farm whose output another farm still makes costs nothing`() = testApplication {
        setupRoutes()
        val (worldId, _) = world("Covered Stopped World")
        createProject(worldId, "Old Log Farm", state = "DECOMMISSIONED").also { produce(it, log) }

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        val stopped = body.substringAfter("STOPPED · 1")
        assertContains(stopped, "Old Log Farm")
        assertContains(stopped, "nothing it made is missing now")
        assertFalse(stopped.contains("no other farm covers"), "Log Farm still makes the oak logs")

        deleteWorld(worldId)
    }

    // ---- fixtures ---------------------------------------------------------------------------

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

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(CreateWorldInput(name = name, description = "test", version = version))
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

    private fun gather(projectId: Int, item: Item, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            }
        ).process(Unit)
    }

    private fun produce(projectId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 0)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
            }
        ).process(Unit)
    }
}
