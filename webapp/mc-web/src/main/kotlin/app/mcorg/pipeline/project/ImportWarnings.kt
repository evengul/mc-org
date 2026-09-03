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
     * MCO-321: obtainable, with an ordinary-looking source, and still capped at a number the
     * build has already exceeded. A dragon egg has a plain block loot table, so the graph sees
     * a source and says nothing; a world nonetheless contains exactly one of them forever.
     *
     * This kind exists because that case falls between the other two: obtainable according to
     * the graph, and not a grind you can simply do more of. Sitting between them is the point.
     * The quantity is not a hint that the gathering is long — it is a statement that the
     * gathering cannot finish. Why a given item is capped, and what a design implies by
     * exceeding it, lives per item in [LIMITED_SUPPLY_NOTES]: the category cannot say it for
     * all of them without going vague.
     */
    LIMITED_SUPPLY(
        chip = "Limited supply",
        heading = "Hard limit in a world",
        explanation = "A world contains a fixed number of these and gathering cannot make more. A design needing several is relying on a duplication trick."
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

/**
 * [detail] is the per-item sentence for kinds whose reason is item-specific — currently only
 * [ImportWarningKind.LIMITED_SUPPLY], where "one per world, and only from the first dragon" is
 * a fact about the dragon egg rather than about the category. Null everywhere else, and the
 * category's own [ImportWarningKind.explanation] is the fallback.
 */
data class ImportWarning(
    val item: Item,
    val amount: Int,
    val kind: ImportWarningKind,
    val detail: String? = null,
) {
    /** What to say about this row: the item's own reason when it has one, else the category's. */
    val message: String get() = detail ?: kind.explanation
}

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
        classify(item, graph)?.let { kind ->
            ImportWarning(
                item = item,
                amount = amount,
                kind = kind,
                detail = if (kind == ImportWarningKind.LIMITED_SUPPLY) LIMITED_SUPPLY_NOTES[item.id] else null,
            )
        }
    })

/**
 * Graph truth outranks the curated lists: if nothing produces an id, "creative only" is the
 * more useful thing to say about it than "slow to gather", whatever the curation thinks.
 *
 * Below that, a hard cap outranks a grind. Nothing is in both curated collections today, but
 * the order is the one that stays true if something ever is: "you cannot get this many" is a
 * bigger fact about a row than "this will take a while".
 */
private fun classify(item: Item, graph: ItemSourceGraph?): ImportWarningKind? = when {
    graph != null && !graph.produces(item.id) -> ImportWarningKind.UNOBTAINABLE
    item.id in LIMITED_SUPPLY_NOTES -> ImportWarningKind.LIMITED_SUPPLY
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
 * Items a world contains a fixed number of, each with the sentence saying why (MCO-321).
 *
 * ## Why this is one item long
 *
 * The bar is a **hard per-world cap**, not scarcity. Nearly everything that feels rare in
 * Minecraft turns out to be renewable or unlimited-per-world once you look: elytra and echo
 * shards come from structures that an infinite world generates infinitely many of, nether
 * stars and totems from farmable mobs, hearts of the sea from buried treasure in every ocean.
 * Those are grinds, and they belong in [EXPENSIVE_ITEM_IDS] where several of them already are.
 *
 * The dragon egg is the one vanilla item whose supply has a ceiling: the first Ender Dragon
 * drops one, respawned dragons drop none, and no recipe or loot table produces another. A
 * design asking for 55 is not asking for a long expedition — it is asking for something no
 * amount of play produces, which is a different sentence and so a different category.
 *
 * Adding to this map means finding another item with the same shape. "Rare" is not enough;
 * "the world stops producing them after N" is the test.
 *
 * ## Why "needs a duplication trick" is not its own kind
 *
 * It would have exactly these members. Needing to dupe is the *consequence* of the cap, not an
 * independent property — the dragon egg is the reason both sentences get said, so they are one
 * warning with one membership list. And the other duplication in this family of builds, TNT
 * duping, has no material row at all: it is a mechanic, so nothing an item-keyed classifier
 * sees could ever carry that warning. A kind that can only ever be attached to the rows this
 * one already covers is a second name for this one.
 *
 * ## Why the row still arrives checked
 *
 * These are functional, not decorative. The 55 dragon eggs in a 1.16-era world eater are one
 * per TNT duper, alongside the same count of dead horn coral fan and detector rail — the
 * quantity tracks the module count because the build genuinely needs them. Unchecking the row
 * does not hand the user a build that works, so offering the exclusion would be offering a
 * worse answer than the truth. The value here is naming *what kind of problem this is* before
 * the gathering starts, not proposing a way out of it.
 */
private val LIMITED_SUPPLY_NOTES = mapOf(
    "minecraft:dragon_egg" to
        "a world yields exactly one, and only from the first dragon. Designs needing more than one rely on egg duplication.",
)

/**
 * Obtainable, but each unit is an expedition rather than a mining trip. Curated on purpose —
 * "expensive" is about the shape of the grind, which the graph does not model. Kept to items
 * whose cost is obvious in hindsight and invisible in a material list.
 *
 * Items with no source at all are deliberately absent: they are found by the graph, not listed
 * here, so this set never has to keep up with what a version happens to make craftable. Items
 * with a hard per-world cap are absent too — they are [LIMITED_SUPPLY_NOTES]'s, because no
 * amount of expedition produces the 55th one.
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
