package app.mcorg.item

/**
 * Maps a Minecraft item id onto one of the 73 Seam item glyphs.
 *
 * Seam draws its own item icons rather than extracting Mojang's textures: the Minecraft Usage
 * Guidelines define "assets" to include textures, graphics and models, and bar redistributing game
 * files. Serving extracted PNGs from Seam would be exactly that.
 *
 * The set is small because **shape is drawn once and material is applied as colour**. Minecraft ids
 * split cleanly into a shape (`_slab`, `_door`, `_ingot`) and a material (`oak_`, `cyan_`,
 * `weathered_`). 73 glyphs plus five [TintAxis]es therefore cover every real item id — 16 wool
 * textures are one glyph and a colour, not sixteen drawings.
 *
 * Deliberately placed outside the presentation layer: ingestion resolves ids too, to report items
 * that no rule covers (MCO-475), and a pipeline step must not import templating.
 */
object ItemGlyph {

    /** How a glyph's material is expressed once its shape is drawn. */
    enum class TintAxis { COLOUR, WOOD, METAL, OXIDATION, MATERIAL }

    /**
     * @param axis the tint dimension this shape varies along, or null when the glyph is drawn once
     *   and never tinted. [TintAxis.MATERIAL] means "varies by material, but we deliberately do not
     *   tint it" — shaped blocks (stairs, slabs, walls) span far too many material families to
     *   colour meaningfully, so they render as shape alone and the written name carries the rest.
     */
    data class Glyph(val name: String, val axis: TintAxis? = null)

    val COLOURS = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )
    val WOODS = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
        "pale_oak", "bamboo", "crimson", "warped",
    )
    val METALS = listOf(
        "wooden", "stone", "copper", "iron", "golden", "diamond", "netherite",
        "chainmail", "leather", "turtle",
    )
    val OXIDATION = listOf("copper", "exposed", "weathered", "oxidized")

    fun valuesFor(axis: TintAxis): List<String> = when (axis) {
        TintAxis.COLOUR -> COLOURS
        TintAxis.WOOD -> WOODS
        TintAxis.METAL -> METALS
        TintAxis.OXIDATION -> OXIDATION
        TintAxis.MATERIAL -> emptyList()
    }

    /**
     * Ids that live in `minecraft_items` but can never be a build material: block-states and
     * technical ids. They resolve to no glyph *and* that is not a gap — [unmapped] skips them so the
     * MCO-475 report stays signal.
     */
    private val NOT_AN_ITEM = Regex(
        "^(air|cave_air|void_air|water|lava|fire|soul_fire|bubble_column|moving_piston|" +
            "nether_portal|end_portal|end_gateway|end_portal_frame|frosted_ice|redstone_wire|" +
            "cocoa|carrots|potatoes|beetroots|kelp_plant|tripwire|sweet_berry_bush|" +
            "cave_vines_plant|cave_vines|chorus_plant|twisting_vines_plant|weeping_vines_plant|" +
            "bamboo_sapling|tall_seagrass|attached_melon_stem|attached_pumpkin_stem|melon_stem|" +
            "pumpkin_stem|set_spawn|barrier|light|structure_block|structure_void|jigsaw|" +
            "command_block|chain_command_block|repeating_command_block|debug_stick|test_block|" +
            "test_instance_block|spawner|trial_spawner|vault|powder_snow_cauldron|water_cauldron|" +
            "lava_cauldron|potted_\\w+|wall_torch|soul_wall_torch|redstone_wall_torch|" +
            ".*_wall_sign|.*_wall_hanging_sign|.*_wall_fan|.*_wall_head|.*_wall_skull|" +
            ".*_wall_banner|pale_hanging_moss|big_dripleaf_stem|piston_head|dried_ghast)$"
    )

    /**
     * First match wins, so **order is the specification**.
     *
     * Shape rules come before material rules. A broad material rule placed early silently swallows
     * shapes made of it — a `copper` rule above the tool rules turns `copper_axe`, `copper_boots`
     * and `copper_bulb` into copper blocks, and nobody notices, because a wrong icon still looks
     * like an icon. That is why `copper` is last.
     */
    private val RULES: List<Pair<Regex, Glyph>> = listOf(
        // --- shaped building blocks: the shape reads louder than the material ---
        rule("_stairs$", "stairs", TintAxis.MATERIAL),
        rule("_slab$", "slab", TintAxis.MATERIAL),
        rule("_wall$", "wall-block", TintAxis.MATERIAL),
        rule("_fence_gate$", "fence-gate", TintAxis.WOOD),
        rule("_fence$", "fence", TintAxis.WOOD),
        rule("_bars$|^iron_bars$|_grate$|^chain$|_chain$", "bars", TintAxis.OXIDATION),
        rule("_door$", "door", TintAxis.MATERIAL),
        rule("_trapdoor$", "trapdoor", TintAxis.MATERIAL),
        rule("_button$", "button", TintAxis.MATERIAL),
        rule("_pressure_plate$", "pressure-plate", TintAxis.MATERIAL),
        rule("_sign$|^sign$", "sign", TintAxis.WOOD),
        rule("_pane$", "glass-pane", TintAxis.COLOUR),
        rule("_carpet$|^moss_carpet$|^pale_moss_carpet$", "carpet", TintAxis.COLOUR),
        rule("_bed$", "bed", TintAxis.COLOUR),
        rule("_candle$|^candle$", "candle", TintAxis.COLOUR),
        rule("_banner_pattern$", "banner-pattern"),
        rule("_banner$", "banner", TintAxis.COLOUR),
        rule("shulker_box$", "shulker-box", TintAxis.COLOUR),
        rule("^bundle$|_bundle$", "bundle", TintAxis.COLOUR),
        rule("^chest$|^trapped_chest$|^ender_chest$|^barrel$|_chest$", "chest", TintAxis.OXIDATION),
        rule("_shelf$|^shelf$", "shelf", TintAxis.WOOD),
        // --- the wood chain ---
        rule("_log$|_stem$|_wood$|_hyphae$|^bamboo_block$|^stripped_bamboo_block$", "log", TintAxis.WOOD),
        rule("_planks$|_mosaic$", "planks", TintAxis.WOOD),
        rule("_sapling$|_propagule$", "sapling", TintAxis.WOOD),
        rule("_leaves$", "leaves", TintAxis.WOOD),
        // --- ores, metals, gems ---
        rule("_ore$|^ancient_debris$", "ore"),
        rule("^raw_\\w+$|^netherite_scrap$", "raw-ore", TintAxis.METAL),
        rule("_ingot$", "ingot", TintAxis.METAL),
        rule("_nugget$", "nugget", TintAxis.METAL),
        rule(
            "^diamond$|^emerald$|^lapis_lazuli$|^amethyst_shard$|^quartz$|^prismarine_crystals$|" +
                "^prismarine_shard$|^echo_shard$|^nether_star$|^heart_of_the_sea$|^flint$|" +
                "^clay_ball$|^coal$|^charcoal$|^resin_clump$|^heavy_core$|^breeze_rod$|^blaze_rod$|" +
                // 26.2 added a cinnabar/sulfur mineral family: the raw drops read as gems, while
                // the chiseled/polished/spike block forms fall through to `stone` below.
                "^cinnabar$|^sulfur$|^potent_sulfur$",
            "gem",
        ),
        rule(
            "^(iron|gold|diamond|emerald|netherite|copper|lapis|redstone|coal|raw_iron|raw_gold|" +
                "raw_copper|amethyst|quartz|bone|resin|slime|honey|dried_kelp|nether_wart|" +
                "warped_wart|hay)_block$",
            "mineral-block", TintAxis.METAL,
        ),
        rule("amethyst", "amethyst"),
        rule("_bricks$|^bricks$|^brick$|^nether_brick$|^resin_brick$|^packed_mud$", "bricks", TintAxis.MATERIAL),
        rule("terracotta$", "terracotta", TintAxis.COLOUR),
        rule("_concrete$|_concrete_powder$", "concrete", TintAxis.COLOUR),
        rule("_glass$|^glass$|^tinted_glass$", "glass", TintAxis.COLOUR),
        rule("^sand$|^red_sand$|^gravel$|^suspicious_sand$|^suspicious_gravel$|^soul_sand$|^soul_soil$", "sand"),
        rule(
            "^clay$|^dirt$|^coarse_dirt$|^rooted_dirt$|^podzol$|^mycelium$|^grass_block$|^mud$|" +
                "^farmland$|^dirt_path$|^moss_block$|^pale_moss_block$",
            "dirt",
        ),
        rule("^ice$|^packed_ice$|^blue_ice$|^snow$|^snow_block$|^powder_snow$|^snowball$", "ice"),
        rule(
            "stone|cobblestone|deepslate|granite|diorite|andesite|tuff|calcite|basalt|blackstone|" +
                "sandstone|prismarine|quartz|purpur|netherrack|obsidian|bedrock|magma_block|" +
                "dripstone|^sponge$|^wet_sponge$|^cobweb$|^infested_|cinnabar|sulfur",
            "stone",
        ),
        // --- redstone ---
        rule(
            "^redstone$|^repeater$|^comparator$|^redstone_torch$|^redstone_lamp$|^lever$|" +
                "^tripwire_hook$|^target$|^daylight_detector$|^observer$|^dispenser$|^dropper$|" +
                "^hopper$|^piston$|^sticky_piston$|^slime_block$|^honey_block$|^note_block$|" +
                "^rail$|_rail$|^lightning_rod$|_lightning_rod$|sculk_sensor$|_bulb$|^crafter$",
            "redstone",
        ),
        // --- tools, combat, gear ---
        rule("_pickaxe$", "pickaxe", TintAxis.METAL),
        rule("_axe$", "axe", TintAxis.METAL),
        rule("_shovel$", "shovel", TintAxis.METAL),
        rule("_hoe$", "hoe", TintAxis.METAL),
        rule("_sword$|_spear$", "sword", TintAxis.METAL),
        rule(
            "_helmet$|_chestplate$|_leggings$|_boots$|^turtle_helmet$|^elytra$|^shield$|" +
                "_nautilus_armor$|^wolf_armor$",
            "armor", TintAxis.METAL,
        ),
        rule(
            "^bow$|^crossbow$|^arrow$|_arrow$|^trident$|^mace$|^fishing_rod$|^wind_charge$|" +
                "^firework_rocket$|^firework_star$|^fire_charge$",
            "bow",
        ),
        rule(
            "_horse_armor$|_harness$|^harness$|^saddle$|^lead$|^name_tag$|^carrot_on_a_stick$|" +
                "^warped_fungus_on_a_stick$",
            "saddlery",
        ),
        // --- mob drops ---
        rule("_spawn_egg$", "spawn-egg"),
        rule("_head$|_skull$", "mob-head"),
        rule("^egg$|_egg$", "egg"),
        rule(
            "^bone$|^bone_meal$|^string$|^spider_eye$|^gunpowder$|^ender_pearl$|^ender_eye$|" +
                "^blaze_powder$|^ghast_tear$|^slime_ball$|^magma_cream$|^phantom_membrane$|" +
                "^rabbit_foot$|^rabbit_hide$|^leather$|^feather$|^ink_sac$|^glow_ink_sac$|" +
                "^shulker_shell$|scute$|^nautilus_shell$|^dragon_breath$|^totem_of_undying$|" +
                "^goat_horn$|^trial_key$|^ominous_trial_key$|^frogspawn$|^popped_chorus_fruit$|^sugar$",
            "mob-drop",
        ),
        // --- food and farming ---
        rule(
            "^wheat$|_seeds$|^carrot$|^potato$|^beetroot$|^sugar_cane$|^bamboo$|^melon$|^pumpkin$|" +
                "^carved_pumpkin$|_melon.*$|^melon_slice$|^cocoa_beans$|^nether_wart$|^sweet_berries$|" +
                "^glow_berries$|^kelp$|^dried_kelp$|^cactus$|^chorus_fruit$|^chorus_flower$",
            "crop",
        ),
        rule(
            "^cooked_|^beef$|^porkchop$|^chicken$|^mutton$|^rabbit$|^cod$|^salmon$|^tropical_fish$|" +
                "^pufferfish$|^bread$|^cookie$|^cake$|_cake$|^pumpkin_pie$|^apple$|^golden_apple$|" +
                "^enchanted_golden_apple$|^baked_potato$|^poisonous_potato$|^rotten_flesh$|stew$|" +
                "soup$|^honey_bottle$|^milk_bucket$|^golden_carrot$|^bowl$",
            "food",
        ),
        rule("mushroom|fungus$|_roots$|_nylium$|^shroomlight$|^nether_sprouts$", "mushroom"),
        rule(
            "^dandelion$|^golden_dandelion$|^poppy$|^blue_orchid$|^allium$|^azure_bluet$|_tulip$|" +
                "^oxeye_daisy$|^cornflower$|^lily_of_the_valley$|^wither_rose$|^sunflower$|^lilac$|" +
                "^rose_bush$|^peony$|^torchflower|^pitcher|^spore_blossom$|^pink_petals$|" +
                "^wildflowers$|^cactus_flower$|eyeblossom$",
            "flower",
        ),
        rule(
            "grass$|^fern$|^large_fern$|^dead_bush$|^vine$|vines$|^lily_pad$|^seagrass$|" +
                "^sea_pickle$|coral|^sculk|^glow_lichen$|^hanging_roots$|dripleaf$|azalea|^bush$|" +
                "^firefly_bush$|^leaf_litter$|^creaking_heart$|^dragon_egg$",
            "plant",
        ),
        // --- utility and misc ---
        rule("_dye$", "dye", TintAxis.COLOUR),
        rule("_wool$", "wool", TintAxis.COLOUR),
        rule("bucket$", "bucket"),
        rule(
            "potion$|^glass_bottle$|^experience_bottle$|^fermented_spider_eye$|" +
                "^glistering_melon_slice$|^brewing_stand$|^cauldron$|^ominous_bottle$",
            "potion",
        ),
        rule("book$|^paper$|^bookshelf$|_bookshelf$", "book"),
        rule(
            "^map$|^filled_map$|^compass$|^recovery_compass$|^lodestone_compass$|^clock$|" +
                "^spyglass$|^brush$|^shears$|^flint_and_steel$|^stick$|^end_crystal$",
            "instrument",
        ),
        rule("^music_disc_|^disc_fragment_5$|^jukebox$", "music-disc"),
        rule("_template$", "smithing-template"),
        rule("_sherd$|_shard$|^pottery_shard_|^decorated_pot$|^flower_pot$", "pottery"),
        rule(
            "^torch$|_torch$|^lantern$|_lantern$|campfire$|glowstone|^sea_lantern$|^end_rod$|" +
                "froglight$|^jack_o_lantern$",
            "torch",
        ),
        rule("_boat$|_raft$", "boat", TintAxis.WOOD),
        rule("minecart$", "minecart"),
        rule("^item_frame$|^glow_item_frame$|^painting$|^armor_stand$|golem_statue$", "frame"),
        rule(
            "^crafting_table$|furnace$|^smoker$|anvil$|^enchanting_table$|^grindstone$|" +
                "^stonecutter$|^smithing_table$|^loom$|^cartography_table$|^fletching_table$|" +
                "^composter$|^lectern$|^beacon$|^conduit$|^respawn_anchor$|^lodestone$|^bell$|" +
                "^scaffolding$|^ladder$|^tnt$|^beehive$|^bee_nest$|^honeycomb",
            "workstation",
        ),
        // Last on purpose — see the ordering note above.
        rule("copper", "copper", TintAxis.OXIDATION),
    )

    private fun rule(pattern: String, glyph: String, axis: TintAxis? = null) =
        Regex(pattern) to Glyph(glyph, axis)

    /** Every glyph name in the set, in rule order. */
    val ALL: List<Glyph> = RULES.map { it.second }.distinctBy { it.name }

    /** Strips the `minecraft:` namespace an ingested id carries. */
    fun bare(itemId: String): String = itemId.substringAfter(':')

    /** True when the id is a real, obtainable item rather than a block-state or technical id. */
    fun isRenderable(itemId: String): Boolean = !NOT_AN_ITEM.matches(bare(itemId))

    /** The glyph for [itemId], or null when no rule covers it. */
    fun resolve(itemId: String): Glyph? {
        val id = bare(itemId)
        if (NOT_AN_ITEM.matches(id)) return null
        return RULES.firstOrNull { (pattern, _) -> pattern.containsMatchIn(id) }?.second
    }

    /**
     * The tint value for [itemId] on its glyph's axis, or null when the glyph takes no tint or the
     * id carries no recognisable prefix (a plain `bundle`, an untinted `chest`).
     */
    fun tint(itemId: String): String? {
        val glyph = resolve(itemId) ?: return null
        val axis = glyph.axis ?: return null
        val id = bare(itemId)
        return when (axis) {
            TintAxis.MATERIAL -> null
            // Longest first, so `light_blue` wins over `light` and `dark_oak` over `oak`.
            else -> valuesFor(axis).sortedByDescending { it.length }
                .firstOrNull { id == it || id.startsWith("${it}_") || id.contains("_${it}_") || id.endsWith("_$it") }
        }
    }

    /**
     * Real item ids in [itemIds] that no rule covers. Technical ids are excluded, so a non-empty
     * result is always a genuine gap in the glyph set — the signal MCO-475 reports on.
     */
    fun unmapped(itemIds: Collection<String>): List<String> =
        itemIds.filter { isRenderable(it) && resolve(it) == null }.map { bare(it) }.sorted()
}
