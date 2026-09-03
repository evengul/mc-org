package app.mcorg.domain.model.minecraft

/**
 * A block that stands for a different item once it is placed in the world.
 *
 * Minecraft separates the thing you carry from the thing that occupies a cell, and not always
 * by name: you place `minecraft:redstone` and the world holds `minecraft:redstone_wire`. Two
 * parts of Seam need to know that and they need *different halves of it*, which is why this
 * carries a [Relation] rather than being a plain map.
 *
 * - **Importing a build** asks "what does this cell cost me" — it wants the item for every
 *   block here, whatever the relation.
 * - **Planning** asks "is breaking this block a way of *getting* the item, or a way of getting
 *   it *back*" — it wants only [Relation.REVERSIBLE], and getting that wrong is a live bug in
 *   both directions.
 *
 * This lives in `mc-domain` because it is neither module's policy: it is a fact about
 * Minecraft, and `mc-domain` is the only module both `mc-engine` and `mc-web` can see. The
 * import door's genuine *policy* — that nine million air blocks are never worth mentioning, and
 * that a water cell costs one bucket — stays in `mc-web` where it belongs.
 *
 * Keeping one table with two readers is the whole point. The import-side list has drifted from
 * a second copy twice already (MCO-396 offered `Air (Block)` x 9,389,854; MCO-308 offered 592
 * `Redstone Wire (Block)` and then called them creative-only), and both were the same table
 * written down in two places.
 */
data class PlacedForm(
    val blockId: String,
    val itemId: String,
    val relation: Relation,
) {
    enum class Relation {
        /**
         * Placing the item makes the block, and breaking it hands the same amount back.
         *
         * **This is the only relation that means "circular".** Breaking placed redstone dust to
         * obtain redstone dust is re-collecting what you put down, exactly as breaking a placed
         * beacon is — the planner must never offer it as a way of acquiring the item, however
         * cheap the arithmetic makes it look. Before this distinction existed the check compared
         * the block's name to the item's name, so `blocks/beacon.json` was caught and
         * `blocks/redstone_wire.json` was not, and the cost model routed redstone through
         * breaking placed wire.
         */
        REVERSIBLE,

        /**
         * Planting the item grows the block, which yields **more than was planted**.
         *
         * Not circular — this is production, and treating it as circular is how a planner ends
         * up sourcing wheat from a shipwreck. What it costs is mostly *time*, which the graph
         * does not model at all; giving these a real source is MCO-492's job, not this file's.
         * Recorded here so that when it is done, what counts as a crop is already written down.
         */
        GROWS,

        /**
         * Breaking the block yields the item, but the item alone cannot make the block — a tool
         * or a bucket stands between them, or the block only generates naturally.
         *
         * Farmland needs a hoe, a dirt path needs a shovel, a filled cauldron needs a bucket,
         * and suspicious sand cannot be placed at all. So breaking one is a legitimate way to
         * end up holding the item and must not be treated as circular, even though the block is
         * plainly "a placed form of" it for import purposes.
         */
        HARVEST_ONLY,
    }
}

/**
 * Every placed form Seam knows about, and the two views its readers need.
 *
 * The list is curated. Mojang's data does not state these relationships — a loot table says
 * `blocks/farmland.json` drops dirt, and nothing says whether you could have placed it.
 */
object PlacedForms {

    val ALL: List<PlacedForm> = buildList {
        // ── Crops and plants: planting yields more than was planted ──────────────
        fun grows(block: String, item: String) =
            add(PlacedForm("minecraft:$block", "minecraft:$item", PlacedForm.Relation.GROWS))

        grows("carrots", "carrot")
        grows("potatoes", "potato")
        grows("beetroots", "beetroot_seeds")
        grows("cocoa", "cocoa_beans")
        grows("melon_stem", "melon_seeds")
        grows("attached_melon_stem", "melon_seeds")
        grows("pumpkin_stem", "pumpkin_seeds")
        grows("attached_pumpkin_stem", "pumpkin_seeds")
        grows("cave_vines", "glow_berries")
        grows("cave_vines_plant", "glow_berries")
        grows("kelp_plant", "kelp")
        grows("bamboo_sapling", "bamboo")
        grows("sweet_berry_bush", "sweet_berries")
        grows("tall_seagrass", "seagrass")
        grows("twisting_vines_plant", "twisting_vines")
        grows("weeping_vines_plant", "weeping_vines")
        grows("big_dripleaf_stem", "big_dripleaf")
        grows("pitcher_crop", "pitcher_pod")
        grows("torchflower_crop", "torchflower_seeds")

        // The crops whose block and item share a name (MCO-501). Every entry above was written
        // because the two names *differ* and a name comparison would miss the pair — which
        // quietly made this list the set of crops the planner happened to get right. The ones
        // below were caught by that name match instead and penalised as "breaking what you
        // placed", so harvesting wheat scored -100 while harvesting carrots scored 100, on the
        // strength of Mojang pluralising one block and not the other.
        //
        // They belong here for exactly the reason the others do: planting yields more than was
        // planted, which is production, not re-collection.
        grows("wheat", "wheat")
        grows("melon", "melon")
        grows("pumpkin", "pumpkin")
        grows("sugar_cane", "sugar_cane")
        grows("bamboo", "bamboo")
        grows("nether_wart", "nether_wart")
        grows("kelp", "kelp")
        grows("cactus", "cactus")

        // ── Placed forms you can put down and pick straight back up ──────────────
        fun reversible(block: String, item: String) =
            add(PlacedForm("minecraft:$block", "minecraft:$item", PlacedForm.Relation.REVERSIBLE))

        reversible("redstone_wire", "redstone")
        reversible("tripwire", "string")
        reversible("wall_torch", "torch")

        // ── A tool stands between the item and the block, or it only generates ───
        fun harvestOnly(block: String, item: String) =
            add(PlacedForm("minecraft:$block", "minecraft:$item", PlacedForm.Relation.HARVEST_ONLY))

        harvestOnly("dirt_path", "dirt")           // a shovel
        harvestOnly("farmland", "dirt")            // a hoe
        harvestOnly("suspicious_sand", "sand")     // generates; cannot be placed
        harvestOnly("suspicious_gravel", "gravel")
        harvestOnly("lava_cauldron", "cauldron")   // a bucket
        harvestOnly("water_cauldron", "cauldron")
        harvestOnly("powder_snow_cauldron", "cauldron")

        // Obsidian is the one entry here that *can* be placed, so the name match called breaking
        // it re-collection and scored mining it -100 — below four chests, for a block you find
        // in lava lakes, ruined portals and every End island (MCO-501). Placing obsidian is
        // possible and is not how anyone obtains it; finding it, or making it with a water
        // bucket, is. So the planner's question — "is breaking this a way of *getting* it" —
        // answers yes, which is what everything other than REVERSIBLE means here.
        harvestOnly("obsidian", "obsidian")

        // The rest of the same family: blocks that occur in the world *in bulk* and also have a
        // recipe. `UnitCostModelAdversarialTest`'s Defect 3 named these when it recorded the
        // wheat case, and they fail identically — the name match called each one re-collection,
        // so the planner would rather craft prismarine out of guardian drops than swim into the
        // ocean monument that is built from it.
        //
        // An ocean monument is prismarine, an iceberg is packed and blue ice, a mangrove swamp
        // is mud. Breaking those is how anyone obtains them, which is the question this table
        // answers. That they *also* craft is not a reason to call the mining circular.
        harvestOnly("prismarine", "prismarine")
        harvestOnly("sea_lantern", "sea_lantern")
        harvestOnly("magma_block", "magma_block")
        harvestOnly("blue_ice", "blue_ice")
        harvestOnly("packed_ice", "packed_ice")
        harvestOnly("mud", "mud")
        harvestOnly("mossy_stone_bricks", "mossy_stone_bricks")
        harvestOnly("mossy_cobblestone", "mossy_cobblestone")

        // `mud_bricks` is deliberately NOT here, though it sits beside `mud` in the same defect
        // note and does appear in trail-ruins templates. It appears there as decoration; nobody
        // travels to a trail ruin to collect mud bricks, and they craft from mud in fours. It
        // stays penalised, which is the honest answer for a manufactured building block.
    }

    /**
     * Block id -> the item a placed cell of it costs. Every relation, because an importer
     * counting a build's materials wants the item whatever the direction.
     */
    val gatheredItem: Map<String, String> = ALL.associate { it.blockId to it.itemId }

    /**
     * Block id -> item, for the placed forms only. Breaking one of these is re-collecting what
     * you put down, so a planner must not present it as a way of acquiring the item.
     */
    val reversible: Map<String, String> = ALL
        .filter { it.relation == PlacedForm.Relation.REVERSIBLE }
        .associate { it.blockId to it.itemId }

    /** True when breaking [blockId] only ever returns an item you must already have had. */
    fun isReversibleFormOf(blockId: String, itemId: String): Boolean =
        reversible[blockId] == itemId

    private val byPair: Map<Pair<String, String>, PlacedForm.Relation> =
        ALL.associate { (it.blockId to it.itemId) to it.relation }

    /**
     * The stated relation between [blockId] and [itemId], or null when this table says nothing
     * about the pair.
     *
     * The distinction between "null" and "not REVERSIBLE" is the point, and [isReversibleFormOf]
     * cannot express it. A caller that falls back to comparing *names* when this table is silent
     * — which is what the planner does, because the table lists only the exceptions and the
     * common case really is "placed `minecraft:beacon` is the beacon you carried" — needs to know
     * whether silence means "no opinion" or "an opinion that is not REVERSIBLE". Collapsing the
     * two is what made harvesting wheat score -100 while harvesting carrots scored 100: `wheat`
     * matched on name before this table was ever consulted, and `carrots` did not (MCO-501).
     */
    fun relationOf(blockId: String, itemId: String): PlacedForm.Relation? = byPair[blockId to itemId]
}
