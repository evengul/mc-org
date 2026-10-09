package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-564: reaching a mob is a trip, and a trip is shared by every kill made on it.
 *
 * `ENTITY_FINDING` multiplied the *kill* by how hard the mob is to reach, so every prismarine
 * shard paid for its own journey to an ocean monument and 85 of them came to fourteen hours.
 * Block loot already prices a structure as one visit divided over what the visit yields
 * ([EffortTable.structureFindFactor]); these pin the mob side to the same shape.
 */
class EntityTripTest {

    private val table = EffortTable.DEFAULT

    private fun entity(mob: String) = SourceNode.fromKey("${SourceType.LootTypes.ENTITY.id}:entities/$mob.json")

    private fun item(name: String) = Item("minecraft:$name", name)

    private fun drop(mob: String, item: String, perKill: Double) = ResourceSource(
        type = SourceType.LootTypes.ENTITY,
        filename = "entities/$mob.json",
        producedItems = listOf(item(item) to ResourceQuantity.ExpectedYield(perKill)),
    )

    @Test
    fun `prismarine shards come from guardians, not from the three elders a monument has`() {
        // Both drop one shard a kill on average, so the old table — same multiplier for both —
        // had no opinion and the elder won on its file name. There are three elders per
        // monument and they never come back; the guardians keep spawning.
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(
            listOf(
                drop("elder_guardian", "prismarine_shard", 1.0),
                drop("guardian", "prismarine_shard", 1.0),
            )
        )

        assertEquals(
            "minecraft:entity:entities/guardian.json",
            UnitCostModel(graph).best(item("prismarine_shard"))?.getKey(),
        )
    }

    @Test
    fun `an elder guardian costs more a kill than a guardian`() {
        assertTrue(table.of(entity("elder_guardian")) > table.of(entity("guardian")))
    }

    @Test
    fun `a monument's worth of shards is an afternoon, not fourteen hours`() {
        // The issue's number: 85 shards at ten minutes each. The trip is one trip.
        val shards = 85 * table.of(entity("guardian"))

        assertTrue(shards < 180.0, "85 shards should take under three hours, was ${"%.0f".format(shards)} min")
    }

    @Test
    fun `a mob that comes to you pays no trip at all`() {
        // The tier stays a statement about the fight. A zombie finds you.
        assertEquals(
            1.5 * table.of(SourceType.LootTypes.ENTITY),
            table.of(entity("zombie")),
            1e-12,
        )
    }

    @Test
    fun `the trip does not scale with how long a kill takes`() {
        // A trip is minutes of travel; the action is minutes of fighting. Sweeping the second
        // must not silently resize the first, or a sweep of the entity row measures two things.
        val action = table.of(SourceType.LootTypes.ENTITY)
        val doubled = table.with(SourceType.LootTypes.ENTITY, action * 2)

        val fight = table.tripOf(entity("guardian"))!!.fight
        val tripShare = table.of(entity("guardian")) - action * fight
        val tripShareDoubled = doubled.of(entity("guardian")) - action * 2 * fight

        assertTrue(tripShare > 0.0, "a guardian is a trip away")
        assertEquals(tripShare, tripShareDoubled, 1e-9)
    }
}
