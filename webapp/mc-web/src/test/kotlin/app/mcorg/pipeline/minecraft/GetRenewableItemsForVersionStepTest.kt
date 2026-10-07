package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.config.CachedItemSourceGraph
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-565: a 1.x version borrows villager trades from the newest version that has them, and a
 * failure to find that donor must not be cached as "no donor" — that would drop every trade-only
 * item from the version's "Worth a farm" for the life of the process.
 */
class GetRenewableItemsForVersionStepTest {

    private val old = "1.99.1"
    private val donorVersion = "99.1.0"

    private fun item(id: String) = Item("minecraft:$id", id)
    private fun one(id: String) = item(id) to ResourceQuantity.ItemQuantity(1)

    /** Emeralds from a mob, and apples only from a farmer's trade, which the old version lacks. */
    private val oldGraph = ItemSourceGraphBuilder.buildFromResourceSources(
        listOf(ResourceSource(ResourceSource.SourceType.LootTypes.ENTITY, "entities/vindicator.json", producedItems = listOf(one("emerald"))))
    )
    private val donorGraph = ItemSourceGraphBuilder.buildFromResourceSources(
        listOf(
            ResourceSource(
                ResourceSource.SourceType.TradeTypes.FARMER, "farmer/2/emerald_apple.json",
                requiredItems = listOf(one("emerald")), producedItems = listOf(one("apple")),
            )
        )
    )

    @BeforeEach
    fun setup() {
        CacheManager.renewability.invalidateAll()
        mockkObject(GetItemSourceGraphForVersionStep, VersionsWithTradesStep, LoadRenewabilityInputsStep)
        coEvery { GetItemSourceGraphForVersionStep.cached(old) } returns Result.success(CachedItemSourceGraph(oldGraph, Instant.EPOCH))
        coEvery { GetItemSourceGraphForVersionStep.cached(donorVersion) } returns Result.success(CachedItemSourceGraph(donorGraph, Instant.EPOCH))
        coEvery { GetItemSourceGraphForVersionStep.currentEpoch(any()) } returns null
        coEvery { LoadRenewabilityInputsStep.process(old) } returns
            Result.success(RenewabilityInputs(setOf("minecraft:emerald", "minecraft:apple"), emptyMap()))
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        CacheManager.renewability.invalidateAll()
    }

    @Test
    fun `trades are borrowed from the newest version that has them`() = runBlocking {
        coEvery { VersionsWithTradesStep.process(Unit) } returns Result.success(listOf("1.2.0", donorVersion))

        val result = GetRenewableItemsForVersionStep.process(old)

        assertIs<Result.Success<Set<String>>>(result)
        assertTrue("minecraft:apple" in result.value)
        assertEquals(donorVersion, CacheManager.renewability.getIfPresent(old)?.donorVersion)
    }

    @Test
    fun `a failed donor lookup is a failure, and is not cached`() = runBlocking {
        coEvery { VersionsWithTradesStep.process(Unit) } returns Result.failure(AppFailure.DatabaseError.ConnectionError)

        assertIs<Result.Failure<*>>(GetRenewableItemsForVersionStep.process(old))
        assertNull(CacheManager.renewability.getIfPresent(old), "a cached result without the donor would never be retried")

        // The next request tries again, and gets the trades.
        coEvery { VersionsWithTradesStep.process(Unit) } returns Result.success(listOf(donorVersion))
        val retried = GetRenewableItemsForVersionStep.process(old)
        assertIs<Result.Success<Set<String>>>(retried)
        assertTrue("minecraft:apple" in retried.value)
    }
}
