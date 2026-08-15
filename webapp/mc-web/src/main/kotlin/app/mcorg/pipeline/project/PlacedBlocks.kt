package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item

/**
 * What a *placed* cell costs, for both import doors (MCO-396).
 *
 * A schematic and an idea can both name ids that occupy space in a build without being a
 * thing you gather. There are two honest answers and this file holds the tables for each:
 *
 * * **Nothing at all** ([NON_MATERIAL_FILL], [NON_MATERIAL_BLOCKS]) — air, an extended
 *   piston's head, a nether portal. The material is either absent or already counted as a
 *   separate cell, so the row is dropped and never mentioned.
 * * **A reusable tool** ([FLUID_PLACEMENTS]) — water, lava, powder snow. What you carry is a
 *   bucket, so the row becomes one bucket and the cell count is kept aside as context.
 *
 * These lists used to disagree with the warning-side list in `ImportWarnings.kt`: the
 * schematic mapper dropped five ids while the warning side merely *warned* about eleven. The
 * six in the gap (the fluids and the portals) therefore survived into the project and landed
 * in the plan as `Blocked: … — no feasible source found` — MCO-319 relabelled them without
 * changing that. One set of tables, read by both doors, is what stops that recurring.
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
 * Applies the two tables above to an already-resolved requirement map — the idea import door.
 *
 * The schematic door does this inside [MapSchematicToMaterialsStep], where it is interleaved
 * with the block-state redirects and the `_wall_` handling that only a schematic needs. This
 * is the subset an idea can hit: an idea's requirements are catalog items, not block states,
 * so the ids either match one of these tables or they are already what you gather.
 *
 * Resolving the *rest* of an idea's placed block ids the way the schematic path does is
 * MCO-308 and deliberately not attempted here.
 */
fun normalizePlacedBlocks(requirements: Map<Item, Int>, catalog: List<Item>): SchematicMaterials {
    val byId = catalog.associateBy { it.id }
    val byItem = LinkedHashMap<Item, Int>()
    val fluidCells = LinkedHashMap<Item, Int>()

    requirements.forEach { (item, amount) ->
        if (item.id in NON_MATERIAL_BLOCKS) return@forEach

        // A fluid never falls through to the generic path, even when the version has no
        // bucket for it (powder snow before 1.17). Keeping the fluid id would put back
        // exactly the row this exists to remove — the schematic door filters the same way.
        val placement = FLUID_PLACEMENTS[item.id]
        if (placement != null) {
            byId[placement]?.let { bucket -> fluidCells[bucket] = (fluidCells[bucket] ?: 0) + amount }
            return@forEach
        }

        byItem[item] = (byItem[item] ?: 0) + amount
    }

    fluidCells.keys.forEach { bucket -> byItem[bucket] = (byItem[bucket] ?: 0) + 1 }

    return SchematicMaterials(
        requirements = byItem.map { (item, amount) -> item to amount },
        placedCounts = fluidCells.entries.associate { (bucket, cells) -> bucket.id to cells },
    )
}
