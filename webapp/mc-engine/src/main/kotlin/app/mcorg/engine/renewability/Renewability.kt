package app.mcorg.engine.renewability

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph

/**
 * Which items a farm can produce in one version, derived from its source graph rather than kept
 * as a list (MCO-565).
 *
 * An item is renewable when something proves it: a source whose type proves on its own (a mob
 * drop, a villager trade, fishing…) or a recipe whose every input is renewable, or one of the
 * hand-written [RenewabilityMechanics]. It is the **least** fixed point, so a loop proves nothing:
 * cobblestone ⇄ stone by recipe makes neither renewable; the generator does.
 *
 * It changes no price and no selection. The planner never reads it; "Worth a farm" does.
 *
 * ## The rules
 *
 * - **Never proof:** chest loot, archaeology, the wandering trader (a source of acquisition, not
 *   of farm-scale supply), Hero of the Village gifts (stacked raid farms broke) and elder
 *   guardian drops (they do not respawn).
 * - **A tag input** needs any one renewable member.
 * - **Breaking a block** proves its drops only when the block is itself renewable; the same for
 *   interacting with one. A block nothing produces — `beetroots`, `potatoes`, `cave_vines`, placed
 *   by an item and never obtained as one — counts when any of its drops is renewable. A potted
 *   plant needs the pot and the plant.
 * - **Dupers are not modelled.** Sand, concrete and TNT stay non-renewable until a world can say
 *   it accepts them (MCO-484).
 *
 * Reviewed verdicts are pinned by a committed snapshot per version (`RenewabilitySnapshotTest`),
 * and a new version is reviewed with `renewability-diagnostics`.
 */
class Renewability private constructor(
    private val sources: List<Source>,
    private val mechanics: List<Mechanic>,
    private val registry: Set<String>,
    private val tags: Map<String, Set<String>>,
    /** The version whose villager trades this one borrowed, if any; see [of]. */
    val tradeDonorVersion: String?,
) {

    /** One graph source, flattened: what it makes, what it takes, and which tags it takes. */
    private data class Source(
        val type: String,
        val filename: String,
        val produces: Set<String>,
        val consumes: Set<String>,
        val consumedTags: Set<String>,
    ) {
        val synthetic: Boolean get() = filename.startsWith("synthetic/")
    }

    /** Everything any source or mechanic makes — a block in here is judged as itself. */
    private val produced: Set<String> =
        sources.flatMapTo(hashSetOf()) { it.produces } + mechanics.flatMap { it.produces }

    /** Block-loot drops per block id, for blocks that are never items. */
    private val blockDrops: Map<String, Set<String>> = sources
        .filter { it.type == BLOCK && it.filename.startsWith("blocks/") }
        .groupBy({ blockOf(it.filename) }, { it.produces })
        .mapValues { (_, drops) -> drops.flatMapTo(sortedSetOf()) { it } }

    /** The renewable items. Includes [NOT_MATERIALS] — a portal is lit, not farmed; see there. */
    val renewable: Set<String> = fixedPoint()

    fun isRenewable(itemId: String): Boolean = itemId in renewable

    private fun fixedPoint(): Set<String> {
        val renewable = hashSetOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for (mechanic in mechanics) {
                if (renewable.containsAll(mechanic.requires) && renewable.addAll(mechanic.produces)) changed = true
            }
            for (source in sources) {
                if (renewable.containsAll(source.produces)) continue
                if (whyNot(source, renewable) == null && renewable.addAll(source.produces)) changed = true
            }
        }
        return renewable
    }

    /**
     * What has to be renewable for breaking or using [block] to prove anything: all of the first
     * list, and one of each group in the second.
     */
    private fun blockNeed(block: String): Pair<List<String>, List<List<String>>> {
        if (block in produced) return listOf(block) to emptyList()
        val drops = blockDrops[block].orEmpty().toList()
        if (drops.isEmpty()) return listOf(block) to emptyList()
        if (block.removePrefix("minecraft:").startsWith("potted_")) return drops to emptyList()
        return emptyList<String>() to listOf(drops)
    }

    private fun blockRenewable(block: String, renewable: Set<String>): Boolean {
        val (all, anyOf) = blockNeed(block)
        return renewable.containsAll(all) && anyOf.all { group -> group.any(renewable::contains) }
    }

    /** The block a source breaks or uses, when it is that kind of source. */
    private fun blockActedOn(source: Source): String? = when {
        source.synthetic -> null
        source.type == BLOCK -> blockOf(source.filename)
        source.type == BLOCK_INTERACT -> source.filename.substringAfterLast('/').removeSuffix(".json")
            .let { INTERACT_BLOCK[it] ?: "minecraft:$it" }
        else -> null
    }

    private fun neverReason(source: Source): String? {
        NEVER_TYPES[source.type]?.let { return it }
        if (source.filename.startsWith(HERO_OF_THE_VILLAGE)) {
            return "Hero of the Village gifts: not renewable since stacked raid farms broke"
        }
        if (source.filename.startsWith(ELDER_GUARDIAN)) return "elder guardians do not respawn"
        return null
    }

    /** Null when [source] proves its products given [renewable], else why it does not. */
    private fun whyNot(source: Source, renewable: Set<String>): String? {
        neverReason(source)?.let { return it }
        blockActedOn(source)?.let { block ->
            if (!blockRenewable(block, renewable)) {
                val verb = if (source.type == BLOCK) "breaks" else "interacts with"
                return "$verb ${short(block)}, which is not renewable"
            }
        }
        val missing = source.consumes.filterNot(renewable::contains).sorted() +
            source.consumedTags.filterNot { tag -> tags[tag].orEmpty().any(renewable::contains) }.sorted()
        return if (missing.isEmpty()) null else "needs " + missing.joinToString(", ", transform = ::short)
    }

    // ── Explanation: what the review page and the diagnostics CLI show ────────

    /** One way to make an item, with what it needs and, when it does not count, why. */
    data class Evidence(
        val kind: String,
        val name: String,
        val all: List<String>,
        val tags: List<String>,
        val anyOf: List<List<String>>,
        val never: Boolean,
        val why: String?,
    )

    data class Verdict(val itemId: String, val renewable: Boolean, val proofs: List<Evidence>, val blocked: List<Evidence>)

    /** A version whose villager trades another version borrows; see [of]. */
    data class TradeDonor(val version: String, val graph: ItemSourceGraph)

    /**
     * A verdict with its evidence for every item that has a way to be made, sorted by id. Item
     * ids in evidence are short (`stone`, not `minecraft:stone`); tag ids are not.
     */
    fun explain(): List<Verdict> {
        val bySource = HashMap<String, MutableList<Evidence>>()
        for (source in sources) {
            val evidence = evidenceFor(source)
            for (item in source.produces) bySource.getOrPut(item) { mutableListOf() }.add(evidence)
        }
        for (mechanic in mechanics) {
            val all = mechanic.requires.map(::short).sorted()
            val missing = all.filterNot { "minecraft:$it" in renewable }
            val evidence = Evidence(
                MECHANIC_KIND, mechanic.family, all, emptyList(), emptyList(), never = false,
                why = if (missing.isEmpty()) null else "needs " + missing.joinToString(", "),
            )
            for (item in mechanic.produces) bySource.getOrPut(item) { mutableListOf() }.add(evidence)
        }
        return registry.sorted()
            .filter { it in bySource && it !in NOT_MATERIALS }
            .map { item ->
                val (proofs, blocked) = bySource.getValue(item).partition { it.why == null }
                Verdict(short(item), item in renewable, proofs, blocked)
            }
    }

    /** The members of each tag [explain]'s evidence names, short, limited to items it lists. */
    fun tagMembers(): Map<String, List<String>> {
        val listed = explain().mapTo(hashSetOf()) { it.itemId }
        return sources.filter { s -> s.produces.any { short(it) in listed } }
            .flatMapTo(sortedSetOf()) { it.consumedTags }
            .associateWith { tag -> tags[tag].orEmpty().map(::short).filter { it in listed }.sorted() }
    }

    private fun evidenceFor(source: Source): Evidence {
        var all = source.consumes.map(::short).toSortedSet()
        var anyOf = emptyList<List<String>>()
        blockActedOn(source)?.let { block ->
            val (blockAll, blockAnyOf) = blockNeed(block)
            all = (all + blockAll.map(::short)).toSortedSet()
            anyOf = blockAnyOf.map { group -> group.map(::short).sorted() }
        }
        return Evidence(
            kind = kindOf(source),
            name = source.filename,
            all = all.toList(),
            tags = source.consumedTags.sorted(),
            anyOf = anyOf,
            never = neverReason(source) != null,
            why = whyNot(source, renewable),
        )
    }

    private fun kindOf(source: Source): String = when {
        source.type in RECIPE_TYPES -> "recipe"
        source.type.startsWith(TRADE_PREFIX) && source.type != WANDERING_TRADER -> "villager trade"
        source.synthetic -> "synthetic source"
        else -> KINDS[source.type] ?: source.type
    }

    companion object {
        private const val BLOCK = "minecraft:block"
        private const val BLOCK_INTERACT = "minecraft:block_interact"
        private const val TRADE_PREFIX = "minecraft:trade/"
        private const val WANDERING_TRADER = "minecraft:trade/wandering_trader"
        private const val HERO_OF_THE_VILLAGE = "gameplay/hero_of_the_village/"
        private const val ELDER_GUARDIAN = "entities/elder_guardian.json"
        const val MECHANIC_KIND = "mechanic (hand list)"

        /**
         * Lit or placed in the world rather than gathered, like air or the end portal. The nether
         * portal has a synthetic source so a build containing one plans, which makes it read as
         * renewable here; it is still never a material anyone farms.
         */
        val NOT_MATERIALS: Set<String> = setOf("minecraft:nether_portal")

        private val NEVER_TYPES = mapOf(
            "minecraft:chest" to "chest loot never counts",
            "minecraft:archaeology" to "archaeology never counts",
            WANDERING_TRADER to "wandering trader: acquisition, not renewability",
            "mcorg:ignored_recipe" to "ignored recipe",
            "mcorg:unknown" to "unknown source",
        )

        private val RECIPE_TYPES = setOf(
            "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:crafting_transmute",
            "minecraft:crafting_imbue", "minecraft:stonecutting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:smithing_transform",
        )

        private val KINDS = mapOf(
            BLOCK to "block loot", "minecraft:entity" to "mob drop", "minecraft:gift" to "gameplay drop",
            "minecraft:barter" to "bartering", "minecraft:fishing" to "fishing", "minecraft:shearing" to "shearing",
            "minecraft:entity_interact" to "mob interaction", BLOCK_INTERACT to "block interaction",
            "minecraft:equipment" to "mob equipment", "minecraft:chest" to "chest",
            "minecraft:archaeology" to "archaeology", WANDERING_TRADER to "wandering trader",
        )

        /** Interaction files name their block loosely: `harvest/cave_vine` acts on `cave_vines`. */
        private val INTERACT_BLOCK = mapOf("cave_vine" to "minecraft:cave_vines")

        private fun blockOf(filename: String) = "minecraft:" + filename.substringAfterLast('/').removeSuffix(".json")

        private fun short(id: String) = id.removePrefix("minecraft:")

        /**
         * Renewability for the version [graph] was built from.
         *
         * [registry] is the version's item ids and [tags] every tag's members, both from the
         * ingest: the graph only carries the tags some recipe consumes, and tree growth needs
         * `#minecraft:logs` whether or not a recipe does.
         *
         * [tradeDonor] lends its villager trades to a version that has none of its own — no 1.x
         * version ingests trades, and a trading hall works there as well as in 26.x. Only trades
         * whose every item exists in [registry] are borrowed, and none that take a tag.
         */
        fun of(
            graph: ItemSourceGraph,
            registry: Set<String>,
            tags: Map<String, Set<String>>,
            tradeDonor: TradeDonor? = null,
        ): Renewability {
            val own = flatten(graph)
            val borrowed = tradeDonor?.graph?.let(::flatten).orEmpty()
                .filter { it.type.startsWith(TRADE_PREFIX) && it.type != WANDERING_TRADER }
                .filter { it.consumedTags.isEmpty() && registry.containsAll(it.produces + it.consumes) }
                .map { it.copy(filename = "borrowed from ${tradeDonor?.version}: ${it.filename}") }
            // The graph's tag nodes carry their members from the same table; they fill in for a
            // tag [tags] does not name rather than leave a recipe's input with no members at all.
            val allTags = graphTags(graph) + tags
            return Renewability(
                own + borrowed, RenewabilityMechanics.forVersion(registry, allTags), registry, allTags,
                tradeDonor?.version,
            )
        }

        private fun graphTags(graph: ItemSourceGraph): Map<String, Set<String>> =
            graph.getAllItems().map { it.item }.filterIsInstance<MinecraftTag>()
                .associate { tag -> tag.id to tag.content.mapTo(hashSetOf()) { it.id } }

        /** True when [graph] has villager trades of its own, so it needs no [of] `tradeDonor`. */
        fun hasVillagerTrades(graph: ItemSourceGraph): Boolean =
            graph.getAllSources().any { it.sourceType.id.startsWith(TRADE_PREFIX) && it.sourceType.id != WANDERING_TRADER }

        private fun flatten(graph: ItemSourceGraph): List<Source> =
            graph.getAllSources().sortedBy { it.getKey() }.map { node ->
                val required = graph.getRequiredItems(node).map { it.item }
                Source(
                    type = node.sourceType.id,
                    filename = node.filename,
                    // A produced tag is a family the source drops one of, not an item; the
                    // ingest's produced items already name what it makes.
                    produces = graph.getProducedItems(node).map { it.item }.filterNot { it is MinecraftTag }
                        .mapTo(linkedSetOf()) { it.id },
                    consumes = required.filterNot { it is MinecraftTag }.mapTo(linkedSetOf()) { it.id },
                    consumedTags = required.filterIsInstance<MinecraftTag>().mapTo(linkedSetOf()) { it.id },
                )
            }
    }
}
