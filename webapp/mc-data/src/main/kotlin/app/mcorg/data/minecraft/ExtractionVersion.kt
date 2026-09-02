package app.mcorg.data.minecraft

/**
 * Monotonic version of the data-extraction *output*. Bump [CURRENT] whenever a change to
 * extraction — new or changed synthetic sources, recipe/loot parsers, item/tag mapping —
 * would produce different `ServerData` for the **same** Mojang server jar.
 *
 * Ingestion records the value it ran under (`minecraft_version_ingestion.extraction_version`)
 * and re-ingests any version whose stored value is older than [CURRENT], so a bump triggers
 * exactly one automatic, self-resetting re-ingest per version — no env flag to flip, no stale
 * re-runs. The server-jar SHA check independently re-ingests when Mojang changes the jar.
 *
 * History:
 *  - 1: synthetic obtain-sources (water/honey/concrete/nether star), wall + crop import
 *       mapping, and shaped/shapeless/simple alternative ingredients — from the
 *       gathering-planner review (2026-06).
 *  - 2: drop infested blocks' `minecraft:block` loot tables (infested_stone,
 *       infested_cobblestone, infested_stone_bricks, infested_mossy/cracked/chiseled_stone_bricks,
 *       infested_deepslate) — their base-block Silk Touch drop is a phantom overridden by
 *       InfestedBlock's code-level destroy handling and was winning over crafting as a fake
 *       raw-gather source (MCO-248, 2026-07).
 *  - 3: synthetic sources for the circular and missing acquisitions found by the
 *       `circular`/`unobtainable` score diagnostics — axe-stripping (12 logs/stems), mud,
 *       dirt_path, farmland, and the filled bucket *items* (water/lava/powder snow, the last
 *       two of which had no source at all). Synthetic sources are now filtered against the
 *       version's item registry, so older versions no longer receive entries for items they
 *       don't have (2026-08).
 *  - 4: prune the lang-derived item registry of legacy ids whose replacement is present
 *       (chain/iron_chain, grass/short_grass, scute/turtle_scute, sign/oak_sign) and of
 *       family-label keys that were never items (smithing_template, harness, set_spawn).
 *       Mojang keeps the old lang key after a rename, so both spellings looked like registry
 *       entries while only the new one is ever produced — MCO-313 (2026-08).
 *  - 5: synthetic sources for the two placed blocks a schematic can name that no data file
 *       describes — `powder_snow` (pour the bucket) and `nether_portal` (light the frame,
 *       which costs no material of its own). Both reported "no feasible source found" on the
 *       YAMS import; `nether_portal` at 54 blocks was the larger of the two — MCO-467 (2026-09).
 *  - 6: a recipe's inline alternative list now reuses the vanilla tag that already has exactly
 *       those members instead of minting a second `#mcorg:choice/…` name for it. At 1.21.4 that
 *       folds torch/soul_torch/fire_charge onto `#minecraft:coals` and TNT onto
 *       `#minecraft:smelts_to_glass`, so the plan stops asking "coal or charcoal" twice under
 *       two names — MCO-486 (2026-09).
 *  - 7: a loot table that another table rolls as part of a pool is no longer stored as a source
 *       of its own. Its numbers are conditional on the parent selecting it, so
 *       `gameplay/fishing/treasure.json` read as 1-in-6 per cast for a nautilus shell where the
 *       composed truth already in `gameplay/fishing.json` is 0.0083 — a 20x overstatement to
 *       any consumer taking the best source per item — MCO-491 (2026-09).
 *
 *       Also at 7, sharing the bump and the one re-ingest it triggers: synthetic "plant it and
 *       wait" sources for the thirteen grown crops. Nothing in Mojang's data grows anything, so
 *       a crop's only route was breaking its own block — which is why wheat, the most farmed
 *       item in the game, and bread after it were being planned through chest loot — MCO-492
 *       (2026-09).
 *
 *       And a synthetic in-world source for `obsidian`: pour a water bucket onto still lava.
 *       Its only routes were finding it and breaking an *ender chest*, which drops eight and is
 *       itself made of obsidian — circular in fact, ordinary block loot in the data — MCO-495
 *       (2026-09).
 */
object ExtractionVersion {
    const val CURRENT = 7
}
