package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.PlacedForm
import app.mcorg.domain.model.minecraft.PlacedForms
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode

/**
 * Is breaking this block just re-collecting something you placed?
 *
 * Breaking a block that *is* the item (`blocks/beacon.json` for `minecraft:beacon`) only recovers
 * something already put down — never a natural acquisition. Mining a *different* block that drops
 * the item (diamond ore for diamonds) does not match.
 *
 * ## Why this has its own file
 *
 * It lived on `SelectionScorer`, and MCO-490's audit of that class turned on telling two things
 * apart: tuning, which the cost model replaces, and **knowledge about the game, which it does
 * not**. This is the knowledge. It was established by the sketch failing without it — costing
 * self-block loot as circular *unconditionally* priced `acacia_log` at 25.7 minutes and lost it
 * to a chest, because breaking an acacia log is how you get one. Restoring the gate moved
 * agreement 77.6% → 84.5% and removed all 121 items the model had wrongly called unreachable.
 *
 * So when the scorer was deleted this had to survive it, and living on a class named for a
 * scoring model was always the wrong address: [PlanSelector.acquirable] and [UnitCostModel]
 * both ask this question, and neither is scoring anything when it does.
 *
 * Note this is only half the rule. The other half is the caller's: a self-block-loot source is
 * disqualified **only when a constructive sibling exists** — a recipe, or a game mechanic like
 * concrete-by-water. Without that condition an acacia log has no source at all. This function
 * answers "is it re-collection"; the caller answers "is there another way".
 */
internal fun isSelfBlockLoot(item: MinecraftId, source: SourceNode): Boolean {
    if (source.sourceType != ResourceSource.SourceType.LootTypes.BLOCK) return false
    val stem = source.filename.substringAfterLast('/').substringBeforeLast('.')

    // Asked before anything else, because everything below is a question about *crafting* and
    // this is the question about the *world* (MCO-527). If world generation makes the ground out
    // of this block, breaking it is mining and cannot be re-collection, whatever its name or its
    // recipe say.
    //
    // Without this the gate decided by asking "does it have a recipe", which is right for a
    // beacon and wrong for terracotta — a badlands is made of the stuff, and smelting clay is a
    // way to make it rather than evidence that finding it is cheating. The result was that
    // mining terracotta was removed as an option entirely, so "should I mine a badlands or trade
    // with a mason" was a comparison the model could not hold.
    //
    // Most terrain escaped by luck rather than by rule before this, which is why the bug looked
    // rare: `sand`, `gravel` and `dirt` have no recipe so the gate never fired on them, and
    // `snow_block`'s loot table is `blocks/snow.json` so its stem did not match. Terracotta had
    // both a matching stem and a recipe, and was caught.
    //
    // Structure-only blocks are deliberately *not* covered — see [NaturalBlocks]. A bookshelf is
    // findable in a mansion, not natural, and "go and find one" is priced elsewhere.
    if (NaturalBlocks.isNatural("minecraft:$stem")) return false

    // A block is not always named after the item you placed to make it: you put down
    // `minecraft:redstone` and the world holds `minecraft:redstone_wire`. Comparing names caught
    // `blocks/beacon.json` and missed that one, so the planner offered "break placed redstone
    // dust" as a way to obtain redstone dust. `PlacedForms` is the curated record of which pairs
    // are actually circular, and **it wins**: a crop yields more than was planted, and farmland
    // needs a hoe standing between the dirt and the block, so neither is re-collection.
    //
    // Asking it *first* is the fix in MCO-501. This used to compare names before consulting the
    // table, so a pair the table had an opinion about never reached it when the two names
    // happened to match — and the table lists mostly pairs whose names differ, because those are
    // the ones a name comparison misses. The result was that the table silently governed only
    // half of its own subject: harvesting carrots scored 100 and harvesting wheat scored -100, on
    // nothing but Mojang pluralising one block id. Nine items were demoted and five escaped, from
    // one family.
    PlacedForms.relationOf("minecraft:$stem", item.id)?.let {
        return it == PlacedForm.Relation.REVERSIBLE
    }

    // Silence from the table is not "not circular" — it lists exceptions, and the ordinary case
    // genuinely is that placed `minecraft:beacon` is the beacon you carried. So the name match
    // stays, as the fallback it should always have been.
    return stem == item.id.substringAfterLast(':')
}
