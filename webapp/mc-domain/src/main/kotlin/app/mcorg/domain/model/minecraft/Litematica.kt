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
)
