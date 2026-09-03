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
}
