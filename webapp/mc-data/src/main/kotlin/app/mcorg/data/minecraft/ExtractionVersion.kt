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
 */
object ExtractionVersion {
    const val CURRENT = 1
}
