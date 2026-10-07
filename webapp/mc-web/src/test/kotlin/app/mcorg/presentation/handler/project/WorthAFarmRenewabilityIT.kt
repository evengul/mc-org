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
import app.mcorg.pipeline.project.handleGetProject
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
 * MCO-565 through the real door: the project page derives renewability from the version's
 * stored sources and leaves out of "Worth a farm" what no farm can make.
 *
 * The rules themselves are pinned in mc-engine (`RenewabilityTest`, and the reviewed snapshots);
 * this proves the page reads them from the database for the world's version.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorthAFarmRenewabilityIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 96, 0)
    private val tuff = Item("minecraft:tuff", "Tuff")
    private val blazeRod = Item("minecraft:blaze_rod", "Blaze Rod")

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        val stored = runBlocking {
            StoreMinecraftDataStep.process(
                ServerData(
                    version = version,
                    items = listOf(tuff, blazeRod),
                    sources = listOf(
                        // Mined, and nothing makes the block: not renewable.
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/tuff.json",
                            producedItems = listOf(tuff to ResourceQuantity.ItemQuantity(1)),
                        ),
                        // A mob drop: a blaze farm.
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.ENTITY,
                            filename = "entities/blaze.json",
                            producedItems = listOf(blazeRod to ResourceQuantity.ItemQuantity(1)),
                        ),
                    ),
                )
            )
        }
        assertIs<Result.Success<*>>(stored)

        worldId = runBlocking {
            (CreateWorldStep(user).process(CreateWorldInput(name = "Renewability IT World", description = "test", version = version))
                as Result.Success).value
        }
        projectId = runBlocking {
            (DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('Deepslate Hall', ?, '', 'BUILDING', 'PLANNING', 'PENDING', 0, 0, 0, 'OVERWORLD') RETURNING id"
                ),
                parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) },
            ).process(Unit) as Result.Success).value
        }
        require(tuff, 13_000)
        require(blazeRod, 2_000)
    }

    @Test
    fun `13,000 tuff is not worth a farm, while 2,000 blaze rods are`() = testApplication {
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Worth a farm")
        // Both are over the threshold; only one can be farmed. Counting both would read "2 raw
        // materials need more than".
        assertContains(body, "1 raw material needs more than")
        assertFalse(body.contains("2 raw materials need more than"), "tuff must not be offered a farm")
        assertContains(body, "Tuff", message = "the demand itself is still planned, just not as a farm")
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
                }
            }
        }
    }

    private fun require(item: Item, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            },
        ).process(Unit)
    }
}
