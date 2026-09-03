package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.PlacedForms

/**
 * What a *placed* cell costs, for both import doors (MCO-396, MCO-308).
 *
 * A schematic and an idea can both name ids that occupy space in a build without being a
 * thing you gather. There are three honest answers and this file holds the tables for each:
 *
 * * **Nothing at all** ([NON_MATERIAL_FILL], [NON_MATERIAL_BLOCKS]) — air, an extended
 *   piston's head, a nether portal. The material is either absent or already counted as a
 *   separate cell, so the row is dropped and never mentioned.
 * * **A reusable tool** ([FLUID_PLACEMENTS]) — water, lava, powder snow. What you carry is a
 *   bucket, so the row becomes one bucket and the cell count is kept aside as context.
 * * **A different item** ([REDIRECTS], the `_wall_` rule) — placed redstone dust is redstone,
 *   a wall sign is a sign, farmland is dirt. Same material, different id once placed.
 *
 * [resolvePlacedCells] applies all three and is the **only** entry point either door uses.
 * That single reader is the point of the file. These lists used to disagree with the
 * warning-side list in `ImportWarnings.kt` (MCO-396), and the redirects above were private to
 * the schematic mapper while the idea door took recorded ids verbatim (MCO-308) — so an idea
 * captured from a schematic offered 592 `Redstone Wire (Block)` and the plan called them
 * creative-only. Both bugs were one table with two readers.
 */

/**
 * Air is not a material, in any list, ever — so it is dropped rather than flagged (MCO-305).
 *
 * This is not a hypothetical: importing idea #3 produced a review list whose largest row was
 * `Air (Block)` x 9,389,854, 90% of the build's entire material total. A warning would have
 * been the polite thing to do and the wrong one; there is no world in which a user wants nine
 * million air blocks on their gathering list, so the honest fix is to never offer them.
 */
val NON_MATERIAL_FILL = setOf(
    "minecraft:air",
    "minecraft:cave_air",
    "minecraft:void_air",
)

/**
 * Cells that are not a material of their own, and carry no decision — dropped silently.
 *
 * The piston already accounts for its extended head and the moving block. The placed-effect
 * blocks (fire, soul_fire, bubble_column) and the portals are created in-world from a block
 * below or around them — a separate, already-counted cell — plus a reusable tool: a flint &
 * steel, a water bucket, an eye of ender. Counting the effect would double-count the frame.
 */
val NON_MATERIAL_BLOCKS = NON_MATERIAL_FILL + setOf(
    "minecraft:piston_head",
    "minecraft:moving_piston",
    "minecraft:fire",
    "minecraft:soul_fire",
    "minecraft:bubble_column",
    "minecraft:nether_portal",
    "minecraft:end_portal",
    "minecraft:end_gateway",
)

/**
 * Fluid cells and the item you actually carry to place them.
 *
 * Litematica's own material list is the reference: it shows **Bucket of Water**, the thing
 * you carry, and never the water. Nothing in the game produces `minecraft:water` because
 * water is not an item, which is exactly why these rows used to reach the plan as blocked.
 *
 * The amount is deliberately **not** the cell count. A bucket is reusable, so any number of
 * source blocks is one bucket carried and refilled; the cell count is preserved separately
 * so a review screen can still show it and stay reconcilable against Litematica.
 */
val FLUID_PLACEMENTS = mapOf(
    "minecraft:water" to "minecraft:water_bucket",
    "minecraft:flowing_water" to "minecraft:water_bucket",
    "minecraft:lava" to "minecraft:lava_bucket",
    "minecraft:flowing_lava" to "minecraft:lava_bucket",
    "minecraft:powder_snow" to "minecraft:powder_snow_bucket",
)

/**
 * Placed block-state ids whose gathered item has a *different* id.
 *
 * The table itself now lives in `mc-domain` as [PlacedForms], because the planner needs the
 * same facts and cannot see `mc-web`. This door wants every entry regardless of direction —
 * a cell costs its item whether or not you could have placed it. The planner wants only the
 * reversible ones. Same table, two readers, which is what stopped it drifting before.
 *
 * Crops resolve to the seed/produce you plant or harvest; tool/effect placements resolve to
 * the material left behind. The target must still exist in the version's catalog to be used —
 * otherwise the block falls through to its own id, and from there to a BLOCKED row, which is
 * a truthful "you cannot get this here" rather than a promise of an item the version lacks.
 */
private val REDIRECTS = PlacedForms.gatheredItem

/** What one placed cell costs — the four answers [resolvePlacedCell] can give. */
sealed interface PlacedCell {
    /** Not a material: air, an extended piston's head, a portal. Never mentioned again. */
    data object Dropped : PlacedCell

    /** A reusable tool. The amount is one bucket, whatever the cell count says. */
    data class Fluid(val bucket: Item) : PlacedCell

    /** An ordinary material, in the amount the cells give. */
    data class Material(val item: Item) : PlacedCell

    /**
     * This version's catalog has no such item, and no rule above claimed it.
     *
     * A case rather than a null because the two doors disagree about it. A schematic drops it
     * — the file may have been saved on a later version, and one stray id is not worth
     * refusing an upload over. An idea reports it as a validation error: its version range
     * was checked before this point, so an id outside the catalog means the idea and the
     * world genuinely disagree and someone should hear about it.
     */
    data object Unknown : PlacedCell
}

/** A resolved cell list: what to gather, the fluid cell counts, and what nothing claimed. */
data class PlacedMaterials(
    val requirements: List<Pair<Item, Int>>,
    val placedCounts: Map<String, Int> = emptyMap(),
    val unknown: List<String> = emptyList(),
)

/** True for cells that are not a material of their own and carry no decision. */
fun isDroppedPlacement(blockId: String): Boolean {
    if (blockId in NON_MATERIAL_BLOCKS) return true
    val name = blockId.substringAfter(':')
    return name.startsWith("potted_") || name.startsWith("infested_") || name.endsWith("candle_cake")
}

/**
 * Resolves one placed block-state id to the item a player actually gathers.
 *
 * Order:
 * 1. [isDroppedPlacement] — placed forms that are not a material of their own (an extended
 *    piston's head, potted plants counted as the pot+plant elsewhere, candle cakes,
 *    silverfish-infested blocks).
 * 2. [FLUID_PLACEMENTS] — a fluid is the bucket you carry, and only ever one of them. A fluid
 *    never falls through to the generic path even when the version has no bucket for it
 *    (powder snow before 1.17): keeping the fluid id would put back exactly the row this
 *    exists to remove.
 * 3. [REDIRECTS] — explicit block -> item for placed forms whose gathered item has a
 *    different id.
 * 4. *wall* variants — drop the `_wall_` infix when that yields a real item
 *    (birch_wall_sign -> birch_sign, dead_horn_coral_wall_fan -> dead_horn_coral_fan).
 * 5. Otherwise the id itself, or [PlacedCell.Unknown] when the version has no such item.
 *
 * Truly non-obtainable blocks (budding_amethyst) are intentionally left to resolve to
 * themselves and surface as a BLOCKED row rather than a wrong guess. That row is what the
 * MCO-305 warning strip is *for*; steps 3 and 4 are what stop it being crowded out by rows a
 * player would read as nonsense — 592 "Redstone Wire (Block)" flagged as needing creative
 * mode, when what they need is 592 redstone.
 */
fun resolvePlacedCell(blockId: String, byId: Map<String, Item>): PlacedCell {
    if (isDroppedPlacement(blockId)) return PlacedCell.Dropped
    FLUID_PLACEMENTS[blockId]?.let { bucketId ->
        return byId[bucketId]?.let { PlacedCell.Fluid(it) } ?: PlacedCell.Dropped
    }
    REDIRECTS[blockId]?.let { target -> byId[target]?.let { return PlacedCell.Material(it) } }
    if ("_wall_" in blockId) {
        byId[blockId.replace("_wall_", "_")]?.let { return PlacedCell.Material(it) }
    }
    return byId[blockId]?.let { PlacedCell.Material(it) } ?: PlacedCell.Unknown
}

/**
 * Resolves a whole cell-count map — the one entry point both import doors call (MCO-308).
 *
 * Amounts are **summed** onto the resolved item, not assigned, because resolution collapses
 * ids: a build with both a sign and a wall sign, or with redstone dust placed *and* stored in
 * a chest, must ask for the total rather than whichever id happened to come last.
 *
 * Fluids are appended after the ordinary materials so a review list reads with the build's own
 * ids first, and their cell counts are returned separately — one bucket is the amount to
 * gather, but "placed 4,013×" is what keeps that number reconcilable against Litematica.
 */
fun resolvePlacedCells(counts: Map<String, Int>, byId: Map<String, Item>): PlacedMaterials {
    val byItem = LinkedHashMap<Item, Int>()
    val fluidCells = LinkedHashMap<Item, Int>()
    val unknown = mutableListOf<String>()

    counts.forEach { (blockId, amount) ->
        when (val cell = resolvePlacedCell(blockId, byId)) {
            is PlacedCell.Dropped -> Unit
            is PlacedCell.Unknown -> unknown.add(blockId)
            is PlacedCell.Fluid -> fluidCells[cell.bucket] = (fluidCells[cell.bucket] ?: 0) + amount
            is PlacedCell.Material -> byItem[cell.item] = (byItem[cell.item] ?: 0) + amount
        }
    }

    fluidCells.keys.forEach { bucket -> byItem[bucket] = (byItem[bucket] ?: 0) + 1 }

    return PlacedMaterials(
        requirements = byItem.map { (item, amount) -> item to amount },
        placedCounts = fluidCells.entries.associate { (bucket, cells) -> bucket.id to cells },
        unknown = unknown,
    )
}
