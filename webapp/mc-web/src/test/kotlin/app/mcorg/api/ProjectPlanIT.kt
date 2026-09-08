package app.mcorg.api

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `GET /api/v1/projects/{id}/plan` (MCO-533) — the HUD's "graph items" mode.
 *
 * The distinction this endpoint exists for: `GET /worlds/{id}/projects` returns the project's
 * **target** items, and this returns the **work** — the raw-gather / smelt / craft nodes the
 * planner expands those targets into. A project asking for chests yields "chop logs, craft planks".
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectPlanIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 98, 0)

    private val log = Item("minecraft:oak_log", "Oak Log")
    private val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
    private val birchPlanks = Item("minecraft:birch_planks", "Birch Planks")
    private val chest = Item("minecraft:chest", "Chest")
    private val planksTag = MinecraftTag("#minecraft:planks", "Planks", listOf(oakPlanks, birchPlanks))

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        CacheManager.invalidateAll()
        val serverData = ServerData(
            version = version,
            items = listOf(log, oakPlanks, birchPlanks, chest, planksTag),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/oak_log.json",
                    producedItems = listOf(log to ResourceQuantity.ItemQuantity(1)),
                ),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                    filename = "oak_planks.json",
                    requiredItems = listOf(log to ResourceQuantity.ItemQuantity(1)),
                    producedItems = listOf(oakPlanks to ResourceQuantity.ItemQuantity(4)),
                ),
                // Consumes the tag, so a chest target leaves an OPEN_TAG node — the
                // NEEDS_ATTENTION case the endpoint must return as 200, not an error.
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "chest.json",
                    requiredItems = listOf(planksTag to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(chest to ResourceQuantity.ItemQuantity(1)),
                ),
            ),
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })
        worldId = createWorld()
    }

    // ── The plan ───────────────────────────────────────────────────────────────

    @Test
    fun `the plan is the work, not the targets`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val projectId = createProject()
        insertGatheringItem(projectId, chest.id, chest.name, required = 4)

        val activities = plan(projectId, token)

        // The target is chests; the plan is what you actually go and do about them.
        val tag = activities.single { it.itemId == planksTag.id }
        assertEquals(32, tag.quantity, "4 chests × 8 planks")
        assertEquals("OPEN_TAG", tag.status)
        assertEquals("NEEDS_ATTENTION", tag.activityGroup)
        assertTrue(activities.any { it.itemId == chest.id })
    }

    @Test
    fun `an unresolved tag is a 200 with the node marked, not an error`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val projectId = createProject()
        insertGatheringItem(projectId, chest.id, chest.name, required = 1)

        val response = planResponse(projectId, token)
        assertEquals(HttpStatusCode.OK, response.status)
        val activities = decode(response)
        // A plan full of open questions is a normal answer. Returning an error here would make the
        // HUD show a failure for a project that simply needs a choice made.
        assertTrue(activities.any { it.activityGroup == "NEEDS_ATTENTION" })
    }

    @Test
    fun `quantity survives as a Long on the wire`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val projectId = createProject()
        // Chosen to overflow Int once expanded: 400,000,000 chests × 8 planks = 3.2e9 planks.
        insertGatheringItem(projectId, chest.id, chest.name, required = 400_000_000)

        val activities = plan(projectId, token)
        val tag = activities.single { it.itemId == planksTag.id }
        assertEquals(3_200_000_000L, tag.quantity)
        assertTrue(tag.quantity > Int.MAX_VALUE, "an Int on the wire would have wrapped this")
    }

    @Test
    fun `a fully collected project is an empty plan, not an error`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val projectId = createProject()
        insertGatheringItem(projectId, chest.id, chest.name, required = 4)
        setCollected(projectId, chest.id, collected = 4, required = 4)

        // The engine reports "nothing left to plan" as a validation failure. For a finished
        // project that is not an error the mod should surface — it is an empty list.
        val response = planResponse(projectId, token)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(decode(response).isEmpty())
    }

    // ── Gating ─────────────────────────────────────────────────────────────────

    @Test
    fun `a non-member gets 403`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val projectId = createProject()
        insertGatheringItem(projectId, chest.id, chest.name, required = 1)
        val strangerToken = issueToken(createExtraUser())

        assertEquals(HttpStatusCode.Forbidden, planResponse(projectId, strangerToken).status)
    }

    @Test
    fun `an unknown project is 404`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        assertEquals(HttpStatusCode.NotFound, planResponse(999_999, issueToken()).status)
    }

    @Test
    fun `the plan route rejects a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val projectId = createProject()
        assertEquals(HttpStatusCode.Unauthorized, planResponse(projectId, null).status)
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.planResponse(projectId: Int, token: String?): HttpResponse =
        client.get("/api/v1/projects/$projectId/plan") {
            if (token != null) header("Authorization", "Bearer $token")
        }

    private suspend fun decode(response: HttpResponse): List<PlanActivityDto> =
        apiJson.decodeFromString(ListSerializer(PlanActivityDto.serializer()), response.bodyAsText())

    private suspend fun ApplicationTestBuilder.plan(projectId: Int, token: String): List<PlanActivityDto> {
        val response = planResponse(projectId, token)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val activities = decode(response)
        assertNotNull(activities)
        return activities
    }

    private fun issueToken(user: TokenProfile = this.user): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(user.id, ApiCrypto.sha256Hex(token), "test", null))
        token
    }

    private fun createWorld(): Int = runBlocking {
        (CreateWorldStep(user).process(
            CreateWorldInput("Plan IT World", "test", version)
        ) as Result.Success).value
    }

    private fun createProject(): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ ->
                st.setString(1, "Plan IT Project ${System.nanoTime()}")
                st.setInt(2, worldId)
            }
        ).process(Unit) as Result.Success).value
    }

    private fun insertGatheringItem(projectId: Int, itemId: String, name: String, required: Int): Int =
        runBlocking {
            (DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
                ),
                parameterSetter = { st, _ ->
                    st.setInt(1, projectId); st.setString(2, itemId)
                    st.setString(3, name); st.setInt(4, required)
                }
            ).process(Unit) as Result.Success).value
        }

    /**
     * Progress lives in `resource_gathering_progress`, keyed by `(project_id, item_id)` — not on
     * the `resource_gathering` row. Written through the app's own step rather than raw SQL, so the
     * test cannot drift from how the application actually records progress.
     */
    private fun setCollected(projectId: Int, itemId: String, collected: Int, required: Long) =
        runBlocking {
            UpsertProgressByItemStep.process(
                UpsertProgressByItemInput(
                    projectId = projectId,
                    itemId = itemId,
                    delta = collected,
                    required = required,
                )
            )
        }
}
