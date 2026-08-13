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
 */
object ExtractionVersion {
    const val CURRENT = 4
}
