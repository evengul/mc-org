package app.mcorg.domain.model.minecraft

/**
 * A parsed `.litematic` file.
 *
 * [items] is the whole build's material list. [regions] is the same data before it was
 * flattened — Litematica lets a schematic hold several named subregions, and a build commonly
 * separates the functional part from a decorative shell (MCO-398). Keeping both means callers
 * that only want "what does this cost" stay unchanged, while the import review can offer the
 * regions as groups to include or exclude.
 *
 * **Invariant:** [items] is the per-id sum over [regions]. The reader builds them together.
 * [regions] is empty only for a `Litematica` constructed directly without them (tests, and the
 * idea path, which has no regions to speak of).
 */
data class Litematica(
    val name: String,
    val description: String,
    val author: String,
    val size: Triple<Int, Int, Int>,
    val items: Map<String, Int>,
    val regions: List<LitematicaRegion> = emptyList(),
    /**
     * The part of [items] that the build is stocked with, per the same invariant (MCO-322).
     *
     * See [LitematicaRegion.containerItems] for what this is and why it is a *subset* of the
     * total rather than a separate list.
     */
    val containerItems: Map<String, Int> = emptyMap(),
)

/**
 * One named subregion of a schematic, with the blocks it contains.
 *
 * The name is whatever the author typed in Litematica's schematic editor, so it is arbitrary
 * user text — it may be empty, may be `"Unnamed"` (the default for a region nobody renamed),
 * and may simply repeat the schematic's own name, which is what Litematica does for a
 * single-region save. All three are common in real files; none of them are worth showing as a
 * group header on their own, which is why a single-region schematic renders without one.
 */
data class LitematicaRegion(
    val name: String,
    val items: Map<String, Int>,
    /**
     * How much of [items] came out of a container rather than the block palette (MCO-322).
     *
     * Litematica saves the contents of every chest, hopper, dispenser and dropper along with
     * the blocks, and both used to land in [items] indistinguishable from each other. What is
     * in those containers is normally **part of the build**, deliberately saved with it: the
     * filter items a sorter needs, the redstone a shulker loader loads, the carved pumpkins
     * that keep wither skeletons from despawning. A stocked container is the rule, not someone
     * forgetting to empty a chest.
     *
     * The split is still worth keeping, because the two halves are different kinds of ask.
     * Placed blocks are the structure and their count follows from its shape; container
     * contents are consumables and stock, they occupy no volume, and their quantity is
     * whatever scale the original builder worked at. A real perimeter farm's list was 70%
     * carved pumpkins and a shulker loader's was 96% redstone — both correct, and both worth
     * seeing as stock rather than reading as structure.
     *
     * A **subset of [items], not a sibling of it.** Nothing is excluded on this basis;
     * reading it as a separate list would double-count every stocked chest.
     */
    val containerItems: Map<String, Int> = emptyMap(),
)
