package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource

/**
 * Rewrites a synthetic [choiceTag] to the vanilla tag that already has exactly its members
 * (MCO-486).
 *
 * [choiceTag] mints a `#mcorg:choice/…` id for a recipe's inline alternative list without
 * looking at the version's tag registry, so a set vanilla already names gets a second name.
 * At 1.21.4 that produced two of them: torch/soul_torch/fire_charge spell coal or charcoal
 * inline while campfire references `#minecraft:coals`, and TNT spells sand or red_sand inline
 * while glass references `#minecraft:smelts_to_glass`. Since [app.mcorg.engine.plan.PlanOverrides]
 * keys tag answers by tag id, the plan asked the same question twice under two names and an
 * answer to one did not settle the other.
 *
 * Only *generated* ids are rewritten — a recipe that names a vanilla tag outright is left
 * alone. Two vanilla tags with identical members (`#planks` / `#wooden_tool_materials`,
 * `#stone_crafting_materials` / `#stone_tool_materials`) are Mojang's duplication, not ours;
 * nothing here can fix those, and the plan-time half of MCO-486
 * ([app.mcorg.engine.plan.TagIdentity]) is what folds them into one question.
 *
 * Membership is compared as the *resolved* member id set — [ExtractionContext.contentOfTag]
 * flattens nested tag references first — so a vanilla tag that reaches its members through
 * another tag still matches. Where several vanilla tags share one set, the lexicographically
 * smallest id wins, which keeps the choice stable across ingests.
 */
internal class ChoiceTagCanonicaliser private constructor(
    private val vanillaTagsByMembers: Map<Set<String>, String>
) {

    /**
     * [id] with any synthetic choice tag swapped for its vanilla equivalent. Display name and
     * members are left for `withNames` to resolve from the registry, as for any vanilla tag.
     */
    fun canonicalise(id: MinecraftId): MinecraftId {
        if (id !is MinecraftTag) return id
        if (!id.id.startsWith(SYNTHETIC_PREFIX)) return id
        val vanilla = vanillaTagsByMembers[id.content.map(Item::id).toSet()] ?: return id
        return MinecraftTag(vanilla, ExtractionContext.tagDisplayName(vanilla), id.content)
    }

    /**
     * [source] with every consumed synthetic choice tag canonicalised. Two ingredient slots
     * that canonicalise to the same tag merge, summing their counts — a recipe cannot want
     * "one of these" and "one of the same these" as separate lines.
     */
    fun canonicalise(source: ResourceSource): ResourceSource {
        if (source.requiredItems.none { (id, _) -> id is MinecraftTag && id.id.startsWith(SYNTHETIC_PREFIX) }) {
            return source
        }
        val merged = LinkedHashMap<String, Pair<MinecraftId, ResourceQuantity>>()
        for ((id, quantity) in source.requiredItems) {
            val canonical = canonicalise(id)
            val existing = merged[canonical.id]?.second
            merged[canonical.id] = canonical to plus(existing, quantity)
        }
        return source.copy(requiredItems = merged.values.toList())
    }

    /** Counts add when both are known; anything else keeps what the first slot said. */
    private fun plus(existing: ResourceQuantity?, added: ResourceQuantity): ResourceQuantity = when {
        existing == null -> added
        existing is ResourceQuantity.ItemQuantity && added is ResourceQuantity.ItemQuantity ->
            ResourceQuantity.ItemQuantity(existing.itemQuantity + added.itemQuantity)
        else -> existing
    }

    companion object {
        private const val SYNTHETIC_PREFIX = "#mcorg:choice/"

        fun from(context: ExtractionContext): ChoiceTagCanonicaliser {
            val byMembers = HashMap<Set<String>, String>()
            for (tag in context.tags.keys) {
                val members = membersOf(tag, context.tags, HashSet())
                if (members.size < 2) continue
                byMembers.merge(members, tag) { a, b -> minOf(a, b) }
            }
            return ChoiceTagCanonicaliser(byMembers)
        }

        /**
         * [ExtractionContext.contentOfTag], but able to survive being pointed at *every* tag in
         * the registry rather than only the ones a recipe references.
         *
         * The registry keys tags by base filename, so `tags/item/foot_armor.json` and
         * `tags/item/enchantable/foot_armor.json` are one entry — and the second one's single
         * value is `#minecraft:foot_armor`, which after the collapse is itself. Resolving that
         * recursively never terminates. Nothing referenced it before this, so the plain recursion
         * was never asked; walking the whole registry asks it 31 versions out of 31.
         */
        private fun membersOf(tag: String, tags: Map<String, List<String>>, visiting: MutableSet<String>): Set<String> {
            if (!visiting.add(tag)) return emptySet()
            val members = tags[tag].orEmpty().flatMapTo(HashSet()) { value ->
                if (value.startsWith("#")) membersOf(value, tags, visiting) else listOf(value)
            }
            visiting.remove(tag)
            return members
        }
    }
}
