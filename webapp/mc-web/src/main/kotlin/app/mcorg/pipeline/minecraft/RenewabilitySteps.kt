package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.config.CachedRenewability
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.engine.renewability.Renewability
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.GetWorldVersionStep

/**
 * A version's [Renewability] (MCO-565), cached beside its graph and rebuilt with it.
 *
 * A version with no villager trades of its own — every 1.x version — borrows them from the
 * newest ingested version that has some. That version's graph is then built too, which is the
 * price of a trading hall counting in 1.21 the way it does in 26.x.
 */
object GetRenewabilityForVersionStep : Step<String, AppFailure, Renewability> {

    override suspend fun process(input: String): Result<AppFailure, Renewability> {
        val own = when (val r = GetItemSourceGraphForVersionStep.cached(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        // The donor is chosen on a miss only. A newer version ingested later becomes the donor
        // the next time this version's graph is rebuilt, which is soon enough for a trade list.
        CacheManager.renewability.getIfPresent(input)?.let { hit ->
            val donorNow = hit.donorVersion?.let { GetItemSourceGraphForVersionStep.cached(it).getOrNull()?.builtAt }
            if (hit.graphBuiltAt == own.builtAt && donorNow == hit.donorBuiltAt) return Result.success(hit.renewability)
        }

        val donor = (if (Renewability.hasVillagerTrades(own.graph)) null else newestVersionWithTrades())
            ?.let { version -> GetItemSourceGraphForVersionStep.cached(version).getOrNull()?.let { version to it } }
        val inputs = when (val r = LoadRenewabilityInputsStep.process(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val tradeDonor = donor?.let { (version, cached) -> Renewability.TradeDonor(version, cached.graph) }
        val renewability = Renewability.of(own.graph, inputs.registry, inputs.tags, tradeDonor)
        CacheManager.renewability.put(
            input,
            CachedRenewability(renewability, own.builtAt, donor?.first, donor?.second?.builtAt),
        )
        return Result.success(renewability)
    }

    private suspend fun newestVersionWithTrades(): String? =
        VersionsWithTradesStep.process(Unit).getOrNull()
            ?.mapNotNull { v -> runCatching { MinecraftVersion.fromString(v) }.getOrNull()?.let { it to v } }
            ?.maxWithOrNull { a, b -> a.first.compareTo(b.first) }
            ?.second
}

/**
 * Whether a farm can make an item in the world's version, for "Worth a farm".
 *
 * When renewability cannot be had, every item counts: the roll-up then reads as it did before
 * MCO-565 rather than failing the page or going empty, the same fallback a missing threshold has.
 */
suspend fun isRenewableInWorld(worldId: Int): (String) -> Boolean {
    val renewability = GetWorldVersionStep.process(worldId).getOrNull()
        ?.let { GetRenewabilityForVersionStep.process(it).getOrNull() }
        ?: return { true }
    return renewability::isRenewable
}

/** What [Renewability] reads beyond the graph: the item registry and every tag's members. */
data class RenewabilityInputs(val registry: Set<String>, val tags: Map<String, Set<String>>)

object LoadRenewabilityInputsStep : Step<String, AppFailure.DatabaseError, RenewabilityInputs> {

    private val registryQuery = DatabaseSteps.query<String, Set<String>>(
        sql = SafeSQL.select("SELECT item_id FROM minecraft_items WHERE version = ?"),
        parameterSetter = { ps, version -> ps.setString(1, version) },
        resultMapper = { rs ->
            val ids = hashSetOf<String>()
            while (rs.next()) ids.add(rs.getString("item_id"))
            ids
        }
    )

    private val tagsQuery = DatabaseSteps.query<String, Map<String, Set<String>>>(
        sql = SafeSQL.select("SELECT tag, item FROM minecraft_tag_item WHERE version = ?"),
        parameterSetter = { ps, version -> ps.setString(1, version) },
        resultMapper = { rs ->
            val tags = hashMapOf<String, MutableSet<String>>()
            while (rs.next()) tags.getOrPut(rs.getString("tag")) { hashSetOf() }.add(rs.getString("item"))
            tags
        }
    )

    override suspend fun process(input: String): Result<AppFailure.DatabaseError, RenewabilityInputs> {
        val registry = when (val r = registryQuery.process(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val tags = when (val r = tagsQuery.process(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return Result.success(RenewabilityInputs(registry, tags))
    }
}

/** Ingested versions with at least one villager trade — the wandering trader does not count. */
internal object VersionsWithTradesStep : Step<Unit, AppFailure.DatabaseError, List<String>> {

    private val query = DatabaseSteps.query<Unit, List<String>>(
        sql = SafeSQL.select(
            """
            SELECT DISTINCT version FROM resource_source
            WHERE source_type LIKE 'minecraft:trade/%' AND source_type <> 'minecraft:trade/wandering_trader'
            """.trimIndent()
        ),
        parameterSetter = { _, _ -> },
        resultMapper = { rs ->
            val versions = mutableListOf<String>()
            while (rs.next()) versions.add(rs.getString("version"))
            versions
        }
    )

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<String>> = query.process(input)
}
