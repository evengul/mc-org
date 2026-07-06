package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlanner
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ItemSourceGraphStepsTest {

    private val version = MinecraftVersion.Release(1, 21, 4)

    private val log = Item("minecraft:oak_log", "Oak Log")
    private val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
    private val birchPlanks = Item("minecraft:birch_planks", "Birch Planks")
    private val chest = Item("minecraft:chest", "Chest")
    private val stick = Item("minecraft:stick", "Stick")
    private val planksTag = MinecraftTag("#minecraft:planks", "Planks", listOf(oakPlanks, birchPlanks))

    private fun serverData() = ServerData(
        version = version,
        items = listOf(log, oakPlanks, birchPlanks, chest, stick, planksTag),
        sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "blocks/oak_log.json",
                producedItems = listOf(log to ResourceQuantity.ItemQuantity(1))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.ENTITY,
                filename = "entities/witch.json",
                producedItems = listOf(stick to ResourceQuantity.ExpectedYield(0.33))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "oak_planks.json",
                requiredItems = listOf(log to ResourceQuantity.ItemQuantity(1)),
                producedItems = listOf(oakPlanks to ResourceQuantity.ItemQuantity(4))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "chest.json",
                requiredItems = listOf(planksTag to ResourceQuantity.ItemQuantity(8)),
                producedItems = listOf(chest to ResourceQuantity.ItemQuantity(1))
            )
        )
    )

    @BeforeAll
    fun setup() {
        CacheManager.invalidateAll()
        val stored = runBlocking { StoreMinecraftDataStep.process(serverData()) }
        assertIs<Result.Success<*>>(stored)
    }

    @Test
    fun `persisted rows round-trip into resource sources with names, tags and quantities`() {
        val result = runBlocking { LoadResourceSourcesForVersionStep.process(version.toString()) }

        assertIs<Result.Success<List<ResourceSource>>>(result)
        val sources = result.value
        assertEquals(4, sources.size)

        val witchLoot = sources.single { it.filename == "entities/witch.json" }
        assertEquals(ResourceQuantity.ExpectedYield(0.33), witchLoot.producedItems.single().second)

        val chestRecipe = sources.single { it.filename == "chest.json" }
        assertEquals(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, chestRecipe.type)
        val (tagInput, tagQuantity) = chestRecipe.requiredItems.single()
        assertIs<MinecraftTag>(tagInput)
        assertEquals("#minecraft:planks", tagInput.id)
        assertEquals(setOf("minecraft:oak_planks", "minecraft:birch_planks"), tagInput.content.map { it.id }.toSet())
        assertEquals(ResourceQuantity.ItemQuantity(8), tagQuantity)

        val plankRecipe = sources.single { it.filename == "oak_planks.json" }
        assertEquals("Oak Log", plankRecipe.requiredItems.single().first.name)
        assertEquals(ResourceQuantity.ItemQuantity(4), plankRecipe.producedItems.single().second)
    }

    @Test
    fun `the planner runs end to end on a graph built from persisted rows`() {
        val graph = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<ItemSourceGraph>>(graph)

        val plan = GatheringPlanner.plan(graph.value, listOf(PlanTarget(chest, 4)))

        assertEquals(PlanNodeStatus.RESOLVED, plan.nodes.getValue("minecraft:chest").status)
        // 4 chests x 8 planks each — the tag is open for the user to pick a species.
        assertEquals(32, plan.nodes.getValue("#minecraft:planks").quantity)
        assertEquals(PlanNodeStatus.OPEN_TAG, plan.nodes.getValue("#minecraft:planks").status)
    }

    @Test
    fun `the graph is cached per version and invalidated on re-ingest`() {
        val first = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        val second = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<*>>(first)
        assertIs<Result.Success<*>>(second)
        assertSame((first as Result.Success).value, (second as Result.Success).value)

        CacheManager.onVersionIngested(version.toString())
        val third = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<*>>(third)
        assertNotSame(first.value, (third as Result.Success).value)
    }

    @Test
    fun `an unknown version fails with NotFound instead of an empty graph`() {
        val result = runBlocking { GetItemSourceGraphForVersionStep.process("1.0.0") }

        assertIs<Result.Failure<*>>(result)
        assertTrue((result as Result.Failure).error is AppFailure.DatabaseError.NotFound)
    }

    // ── MCO-252: self-invalidation against the ingestion ledger's completed_at ──────────────

    @Test
    fun `a ledger completed_at strictly after the cached build forces a rebuild`() {
        // Make sure something is cached for this version — a hit or a fresh build, doesn't matter,
        // its builtAt stamp is always <= now().
        val before = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<ItemSourceGraph>>(before)

        // The ledger now reports a completion safely after any prior build. Invalidate the short-TTL
        // epoch cache to simulate the TTL window having elapsed, rather than sleeping in the test.
        seedCompletedLedgerRow(version.toString(), Instant.now().plusSeconds(120))
        CacheManager.versionIngestionEpoch.invalidate(version.toString())

        val after = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<ItemSourceGraph>>(after)
        assertNotSame(before.value, after.value)
    }

    @Test
    fun `a ledger completed_at at or before the cached build keeps serving the cache`() {
        val first = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<ItemSourceGraph>>(first)

        // A completion well before the cached build must not be treated as a fresher signal.
        seedCompletedLedgerRow(version.toString(), Instant.now().minusSeconds(3600))
        CacheManager.versionIngestionEpoch.invalidate(version.toString())

        val second = runBlocking { GetItemSourceGraphForVersionStep.process(version.toString()) }
        assertIs<Result.Success<ItemSourceGraph>>(second)
        assertSame(first.value, second.value)
    }

    /** Upserts a `completed` ledger row for [versionString], stamping `completed_at` as [completedAt]. */
    private fun seedCompletedLedgerRow(versionString: String, completedAt: Instant) {
        val result = runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    """
                    INSERT INTO minecraft_version_ingestion (version, status, completed_at, attempt_count)
                    VALUES (?, 'completed', ?, 1)
                    ON CONFLICT (version) DO UPDATE SET
                        status = 'completed',
                        completed_at = EXCLUDED.completed_at
                    """.trimIndent()
                ),
                parameterSetter = { statement, _ ->
                    statement.setString(1, versionString)
                    statement.setTimestamp(2, Timestamp.from(completedAt))
                }
            ).process(Unit)
        }
        assertIs<Result.Success<*>>(result)
    }
}
