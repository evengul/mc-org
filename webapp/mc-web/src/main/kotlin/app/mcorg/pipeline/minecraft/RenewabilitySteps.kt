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
import java.time.Instant

/** A freshly built [Renewability] with the build instants of the graphs it was derived from. */
data class BuiltRenewability(
    val renewability: Renewability,
    val graphBuiltAt: Instant,
    val donorVersion: String?,
    val donorBuiltAt: Instant?,
)

/**
 * Builds a version's [Renewability] (MCO-565). Uncached: the diagnostics CLI wants the whole
 * explanation, and the web path caches only the set it reads ([GetRenewableItemsForVersionStep]).
 *
 * A version with no villager trades of its own — every 1.x version — borrows them from the
 * newest ingested version that has some, which builds that version's graph too: the price of a
 * trading hall counting in 1.21 the way it does in 26.x. Failing to find or load the donor is a
 * failure, not "no donor": a result without the trades would be cached and quietly drop every
 * trade-only item from the version.
 */
object BuildRenewabilityForVersionStep : Step<String, AppFailure, BuiltRenewability> {

    override suspend fun process(input: String): Result<AppFailure, BuiltRenewability> {
        val own = when (val r = GetItemSourceGraphForVersionStep.cached(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val donorVersion = if (Renewability.hasVillagerTrades(own.graph)) null else {
            when (val r = VersionsWithTradesStep.process(Unit)) {
                is Result.Success -> newest(r.value)
                is Result.Failure -> return r
            }
        }
        val donor = donorVersion?.let { version ->
            when (val r = GetItemSourceGraphForVersionStep.cached(version)) {
                is Result.Success -> r.value
                is Result.Failure -> return r
            }
        }
        val inputs = when (val r = LoadRenewabilityInputsStep.process(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val tradeDonor = if (donorVersion != null && donor != null) Renewability.TradeDonor(donorVersion, donor.graph) else null
        val renewability = Renewability.of(own.graph, inputs.registry, inputs.tags, tradeDonor)
        return Result.success(BuiltRenewability(renewability, own.builtAt, donorVersion, donor?.builtAt))
    }

    private fun newest(versions: List<String>): String? =
        versions.mapNotNull { v -> runCatching { MinecraftVersion.fromString(v) }.getOrNull()?.let { it to v } }
            .maxWithOrNull { a, b -> a.first.compareTo(b.first) }
            ?.second
}

/**
 * The items a farm can make in a version — all "Worth a farm" reads — cached beside the graph.
 *
 * A hit is checked against the ingestion epoch of the version and of its trade donor, the same
 * test the graph cache uses, so a re-ingest of either rebuilds it, and a hit never has to load a
 * graph to find out. The donor is chosen on a miss only: a newer version ingested later becomes
 * the donor the next time this version is rebuilt, which is soon enough for a trade list.
 */
object GetRenewableItemsForVersionStep : Step<String, AppFailure, Set<String>> {

    override suspend fun process(input: String): Result<AppFailure, Set<String>> {
        CacheManager.renewability.getIfPresent(input)?.let { hit ->
            if (isFresh(hit.graphBuiltAt, input) && (hit.donorVersion == null || isFresh(hit.donorBuiltAt, hit.donorVersion))) {
                return Result.success(hit.renewable)
            }
        }
        return BuildRenewabilityForVersionStep.process(input).map { built ->
            built.renewability.renewable.also {
                CacheManager.renewability.put(
                    input, CachedRenewability(it, built.graphBuiltAt, built.donorVersion, built.donorBuiltAt),
                )
            }
        }
    }

    private suspend fun isFresh(builtAt: Instant?, version: String): Boolean =
        builtAt != null &&
            !GetItemSourceGraphForVersionStep.isStale(builtAt, GetItemSourceGraphForVersionStep.currentEpoch(version))
}

/**
 * Whether a farm can make an item in the world's version, for "Worth a farm".
 *
 * When renewability cannot be had, every item counts: the roll-up then reads as it did before
 * MCO-565 rather than failing the page or going empty, the same fallback a missing threshold has.
 * A failure is not cached, so the next request tries again.
 */
suspend fun isRenewableInWorld(worldId: Int): (String) -> Boolean {
    val renewable = GetWorldVersionStep.process(worldId).getOrNull()
        ?.let { GetRenewableItemsForVersionStep.process(it).getOrNull() }
        ?: return { true }
    return renewable::contains
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
