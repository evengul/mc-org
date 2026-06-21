package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * One candidate source for an item, ranked against its siblings.
 *
 * @param source the candidate.
 * @param score the [SelectionScorer] score (higher is better).
 * @param rank position in the ranking, 0 = best.
 */
data class RankedSource(
    val source: SourceNode,
    val score: Int,
    val rank: Int,
)

/**
 * Read-only ranking of an item's candidate sources, for UIs that let a user
 * re-pin a source (the drill picker). This is a thin, side-effect-free view over
 * the **existing** [SelectionScorer] and the exact ordering [PlanSelector] uses
 * internally — it computes nothing new and changes no scoring. It exists only
 * because [SelectionScorer] is module-internal, so callers outside the engine
 * cannot rank candidates themselves.
 *
 * The ordering mirrors `PlanSelector.rank` verbatim: descending score, then
 * recipes ahead of non-recipes on ties, then a stable id tiebreak.
 *
 * Demand matters because the score includes a demand-sensitive recipe-threshold
 * bonus; pass the demand the UI is presenting (e.g. the drill's
 * `TargetTree.quantityIfAlone`). [supplied] and [context] default to "nothing
 * supplied / standard context", which yields the intrinsic ranking of a source;
 * pass the project's real supplied map to match the planner's own choice exactly.
 */
object SourceRanking {

    fun rankSources(
        graph: ItemSourceGraph,
        item: MinecraftId,
        demand: Long,
        supplied: Map<String, SupplySource> = emptyMap(),
        context: PlanContext = PlanContext(),
    ): List<RankedSource> {
        val candidates = graph.getSourcesForItem(item)
        if (candidates.isEmpty()) return emptyList()

        val scorer = SelectionScorer(graph, supplied, context)
        val hasRecipeSibling = candidates.any { it.sourceType.isRecipe() }

        return candidates
            .map { it to scorer.score(item, it, demand, hasRecipeSibling) }
            .sortedWith(
                compareByDescending<Pair<SourceNode, Int>> { it.second }
                    .thenByDescending { it.first.sourceType.isRecipe() }
                    .thenBy { it.first.getKey() }
            )
            .mapIndexed { index, (source, score) -> RankedSource(source, score, index) }
    }
}
