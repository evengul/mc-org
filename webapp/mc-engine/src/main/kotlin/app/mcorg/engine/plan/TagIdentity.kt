package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph

/**
 * Two tags with the same members are one question (MCO-486).
 *
 * A tag id is the plan's identity for a variant choice: [PlanSelector] makes one
 * [PlanNodeStatus.OPEN_TAG] node per tag, and [PlanOverrides.tagMember] answers it by that id.
 * Minecraft hands us several ids for the same set — `#minecraft:planks` and
 * `#minecraft:wooden_tool_materials` are the same twelve planks, `#minecraft:stone_crafting_materials`
 * and `#minecraft:stone_tool_materials` the same three stones — and where both appear in one plan
 * the user is asked the same thing twice in different words, with an answer to one leaving the
 * other open. Nobody holds two independent opinions about which plank.
 *
 * So membership, not the id, decides identity here. Every tag in the graph is mapped to one
 * **representative** of its member set; the selector resolves through that representative, which
 * makes the DAG carry one node (and the plan one question) per distinct set, and reads
 * [PlanOverrides.tagMember] across the whole class, so answering under any of the ids settles all
 * of them — including answers stored before this existed.
 *
 * The representative is the **vanilla** id where there is one, then the lexicographically smallest:
 * `#minecraft:…` names a set the same way in every world and survives a re-ingest, while a
 * `#mcorg:choice/…` id is a synthetic name for one recipe's inline list and can be a content hash.
 * (The extraction half of MCO-486 removes the synthetic name outright wherever a vanilla tag
 * already covers the set; this side still has to hold for graphs ingested before that, and for the
 * vanilla-duplicates-vanilla case, which has nothing upstream to fix.)
 *
 * Only *equal* member sets fold. A subset relationship is a different question with fewer answers
 * — the sandstone choices `{chiseled, cut, plain}` and `{chiseled, plain}` overlap without being
 * equal, and folding them would silently offer an option the narrower recipe does not accept.
 * Those two need nothing anyway: they are one material in different appearances, so
 * [MemberPrior.canonicalFormMember] already answers both without asking.
 */
internal class TagIdentity private constructor(
    /** tag id -> every tag id sharing its member set, representative first. */
    private val classById: Map<String, List<String>>,
    private val tagsById: Map<String, MinecraftTag>,
) {

    /**
     * The tag that stands for [tag]'s member set — [tag] itself when nothing else shares it, or
     * when the graph does not contain it (a caller-supplied tag is answered on its own terms).
     */
    fun representative(tag: MinecraftTag): MinecraftTag {
        val representativeId = classById[tag.id]?.first() ?: return tag
        return tagsById[representativeId] ?: tag
    }

    /** Every id that asks [tag]'s question, representative first — the keys an answer may be under. */
    fun equivalentIds(tag: MinecraftTag): List<String> = classById[tag.id] ?: listOf(tag.id)

    companion object {
        fun of(graph: ItemSourceGraph): TagIdentity {
            val tags = graph.getAllItems().mapNotNull { it.item as? MinecraftTag }
            val byMembers = tags.groupBy { tag -> tag.content.map { it.id }.toSet() }

            val classById = HashMap<String, List<String>>()
            for ((_, sharing) in byMembers) {
                val ids = sharing.map { it.id }.distinct().sortedWith(REPRESENTATIVE_ORDER)
                for (id in ids) classById[id] = ids
            }
            return TagIdentity(classById, tags.associateBy { it.id })
        }

        /** Vanilla before synthetic, then by id — a total order, so the pick is deterministic. */
        private val REPRESENTATIVE_ORDER: Comparator<String> =
            compareBy<String> { if (it.startsWith(SYNTHETIC_PREFIX)) 1 else 0 }.thenBy { it }

        private const val SYNTHETIC_PREFIX = "#mcorg:choice/"
    }
}
