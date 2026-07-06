package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph

/**
 * MCO-246: variant candidates for a concrete item id — the other members of any tag the item
 * belongs to (e.g. `minecraft:oak_planks` -> the rest of `#minecraft:planks`). This is a
 * read-only reuse of the tag/member data already loaded into the graph (`MinecraftTag.content`
 * on an `ItemNode`) — the same data structure the tag-member picker in `DrillView.kt` renders
 * from. No graph construction or scoring logic is touched here.
 *
 * Coverage is bounded by which tags happen to be graph nodes: a tag only becomes a node when
 * some recipe/loot source references it as a choice ingredient (see `mc-engine/CLAUDE.md` +
 * `ItemSourceGraphBuilder`). Real per-species tags (`#minecraft:planks`, `#minecraft:wool`,
 * `#minecraft:logs`, tool-material tags, ...) and synthetic multi-choice recipe tags (colour
 * families for wool/beds/carpets from recolouring recipes) are covered this way. Families with
 * no covering tag anywhere in the recipe/loot data — e.g. door species, since vanilla has no
 * "any door" recipe ingredient — are not, and this returns an empty list for them.
 *
 * @return candidates de-duplicated by id, sorted by name, excluding [itemId] itself. Empty
 *   when [graph] is null or the item belongs to no tag family present in the graph.
 */
fun findVariantCandidates(graph: ItemSourceGraph?, itemId: String): List<Item> {
    if (graph == null) return emptyList()

    val candidates = linkedMapOf<String, Item>()
    for (node in graph.getAllItems()) {
        val tag = node.item as? MinecraftTag ?: continue
        if (tag.content.none { it.id == itemId }) continue
        for (member in tag.content) {
            if (member.id == itemId) continue
            candidates.putIfAbsent(member.id, member)
        }
    }
    return candidates.values.sortedBy { it.name }
}
