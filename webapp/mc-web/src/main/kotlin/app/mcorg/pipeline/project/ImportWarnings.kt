package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.pipeline.resources.getGraphForWorld

/**
 * MCO-305: the painful rows in an import, named *before* the project exists.
 *
 * Nothing here blocks an import. The whole point is that the exclude checkbox next to a
 * command block or a stack of turtle eggs is an informed choice rather than a surprise
 * three hours into gathering. Every warning is advisory and every warned row is still
 * checked by default — we say what we know and leave the decision alone.
 *
 * There used to be a third kind, `NON_MATERIAL`, for rows that are placed rather than
 * gathered. It is gone (MCO-396): warning about a row is the wrong answer when the row
 * should not exist. Those ids are now dropped or resolved to the bucket you carry, in
 * `PlacedBlocks.kt`, before anything gets classified here. Warning *and* keeping them was
 * how water and nether portals reached the plan as permanently blocked nodes.
 */
enum class ImportWarningKind(val chip: String, val heading: String, val explanation: String) {
    /**
     * No source in the item-source graph produces this id, which is the same condition the
     * planner reports as its BLOCKED bucket and the `score-dump unobtainable` report lists.
     * Command blocks, barriers and player heads land here.
     */
    UNOBTAINABLE(
        chip = "Creative only",
        heading = "Not obtainable in survival",
        explanation = "Nothing in this Minecraft version produces these — they need creative mode or an operator command. The plan will show them as blocked."
    ),

    /**
     * Obtainable, but the gathering will be miserable. This one is a judgement call rather
     * than something the graph can answer: a wither skeleton skull *has* a source, and the
     * source is a fortress grind. Curated deliberately, and kept short.
     */
    EXPENSIVE(
        chip = "Slow to gather",
        heading = "Expensive to gather",
        explanation = "Obtainable, but each one is a real expedition. Worth knowing the quantity now rather than after the plan is built."
    ),
}

data class ImportWarning(
    val item: Item,
    val amount: Int,
    val kind: ImportWarningKind,
)

/**
 * The warnings for one import, in a shape the review page can both summarise and index by row.
 */
data class ImportWarnings(val warnings: List<ImportWarning> = emptyList()) {
    private val byItemId: Map<String, ImportWarning> = warnings.associateBy { it.item.id }

    val isEmpty: Boolean get() = warnings.isEmpty()

    fun forItem(itemId: String): ImportWarning? = byItemId[itemId]

    /** Warnings of one kind, largest quantity first — the ones worth reading are the big ones. */
    fun of(kind: ImportWarningKind): List<ImportWarning> =
        warnings.filter { it.kind == kind }.sortedByDescending { it.amount }
}

/**
 * Classifies an import's material list against the world's item-source graph.
 *
 * The graph fetch is the cached per-version one the planner already uses, so this costs a
 * map lookup in the steady state. A world with no ingested version yields a null graph and
 * no [ImportWarningKind.UNOBTAINABLE] warnings — silence beats guessing.
 */
suspend fun computeImportWarnings(worldId: Int, requirements: Map<Item, Int>): ImportWarnings =
    classifyImportWarnings(requirements, getGraphForWorld(worldId))

/** The pure half of [computeImportWarnings], split out so the rules are testable without a world. */
internal fun classifyImportWarnings(requirements: Map<Item, Int>, graph: ItemSourceGraph?) =
    ImportWarnings(requirements.mapNotNull { (item, amount) ->
        classify(item, graph)?.let { ImportWarning(item, amount, it) }
    })

/**
 * Graph truth outranks the curated list: if nothing produces an id, "creative only" is the
 * more useful thing to say about it than "slow to gather", whatever the curation thinks.
 */
private fun classify(item: Item, graph: ItemSourceGraph?): ImportWarningKind? = when {
    graph != null && !graph.produces(item.id) -> ImportWarningKind.UNOBTAINABLE
    item.id in EXPENSIVE_ITEM_IDS -> ImportWarningKind.EXPENSIVE
    else -> null
}

/**
 * Matches by id rather than by [Item] equality — an `Item` is a data class over (id, name),
 * and the catalog's display name need not be byte-identical to the one baked into the graph
 * node. Matching on the name would report perfectly obtainable items as creative-only.
 */
private fun ItemSourceGraph.produces(itemId: String): Boolean =
    getItemNodesByStringId(itemId).any { getSourcesForItem(it.item).isNotEmpty() }

/**
 * Obtainable, but each unit is an expedition rather than a mining trip. Curated on purpose —
 * "expensive" is about the shape of the grind, which the graph does not model. Kept to items
 * whose cost is obvious in hindsight and invisible in a material list.
 *
 * Items with no source at all are deliberately absent: they are found by the graph, not listed
 * here, so this set never has to keep up with what a version happens to make craftable.
 */
private val EXPENSIVE_ITEM_IDS = setOf(
    // Mob-drop grinds
    "minecraft:wither_skeleton_skull",
    "minecraft:nether_star",
    "minecraft:beacon",
    "minecraft:totem_of_undying",
    "minecraft:shulker_shell",
    "minecraft:shulker_box",
    "minecraft:trident",
    "minecraft:nautilus_shell",
    "minecraft:heart_of_the_sea",
    "minecraft:conduit",
    // Structure loot
    "minecraft:elytra",
    "minecraft:enchanted_golden_apple",
    "minecraft:echo_shard",
    "minecraft:recovery_compass",
    "minecraft:sponge",
    "minecraft:wet_sponge",
    // Dimension grinds
    "minecraft:ancient_debris",
    "minecraft:netherite_scrap",
    "minecraft:netherite_ingot",
    "minecraft:netherite_block",
    "minecraft:dragon_breath",
    // Slow to farm at any scale
    "minecraft:turtle_egg",
    "minecraft:sculk_catalyst",
    "minecraft:ochre_froglight",
    "minecraft:verdant_froglight",
    "minecraft:pearlescent_froglight",
)
