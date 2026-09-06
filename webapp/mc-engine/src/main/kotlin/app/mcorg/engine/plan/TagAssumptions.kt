package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph

/**
 * Which open questions are too small to be worth asking (MCO-410).
 *
 * A plan that opens with a wall of variant questions is a plan nobody reads. Most of that wall is
 * genuinely trivial — on the YAMS import, a tail of `#mcorg:choice/…` sets worth a handful of
 * items each — and below some share of the plan, **asking is worse than picking**.
 *
 * ## The threshold is a share of the plan's minutes, and that is the point
 *
 * Not an absolute count: nine items is nothing in a 100,000-block storage system and significant
 * in a starter base, so a count means different things in different builds and would have to be
 * retuned per build. A share of the plan is scale-free — it means the same thing everywhere —
 * and it is only expressible at all because everything is now priced in one unit (MCO-490).
 *
 * The plan's total is taken from its **targets**, not by summing nodes: the targets are what the
 * user asked for, and summing nodes would double-count every shared intermediate.
 *
 * ## What it resolves to, and why it is not a fourth opinion
 *
 * [TagMemberRanking.recommended] — cost first, then [MemberPrior] for members cost cannot
 * separate, then name. That is the same call the drill's picker marks as best and the same one
 * the bulk "answer the remaining questions" action uses, which matters more here than anywhere
 * else: an assumption the user never sees must not be reached by a rule nobody can check against
 * what the picker shows.
 *
 * The `MemberPrior` step is load-bearing rather than decorative. Two of the three live questions
 * on world 3 are **exact cost ties** — `sand`/`red_sand` and `soul_sand`/`soul_soil` — so cost
 * alone would fall through to an id sort and silently freeze `red_sand` because "r" sorts before
 * "s". MCO-410's own argument is why that would be worse than asking: *a wrong visible answer
 * gets corrected and a wrong invisible one does not.*
 *
 * ## A supplied member makes a question free, and that is fine
 *
 * `c(tag)` is the minimum over its members, so a tag with one **supplied** member costs zero and
 * falls below *every* threshold — the share is not small, it is nil, and the number above never
 * gets consulted. On world 3 that is what settles "Blackstone, Cobbled Deepslate or Cobblestone":
 * cobblestone comes from a farm, so the choice is free and is answered rather than asked.
 *
 * That looks alarming and is not, for a structural reason (Even, 2026-09-06): **an open tag is
 * always a recipe ingredient, never a target.** MCO-409 established that — every target is built
 * as `PlanTarget(Item(...))` and tag ids carry a `#` prefix item ids never have — so answering one
 * cannot change what the build ends up containing. It changes an intermediate node and nothing
 * else. A player asking for a hopper gets a hopper whether its stone came from cobblestone or
 * blackstone; the only thing at stake is which pile they gather, and a supplied pile is no work at
 * all.
 *
 * So the threshold is not the only thing keeping this honest, and it does not have to be. The
 * guarantee underneath it is that no assumption is capable of changing the output.
 *
 * ## Never persisted
 *
 * Nothing is written down. Every derivation recomputes this, so an assumption that stops being
 * justified — the build grew, the question is no longer small — returns as a question by itself.
 * An assumption is only ever warranted by the thing being small, so it should not outlive that.
 */
internal object TagAssumptions {

    /**
     * The assumptions this plan justifies, or empty when every open question is big enough to
     * deserve asking.
     *
     * @param threshold share of the plan's minutes below which a question is answered rather than
     *   asked. Zero or less disables the whole mechanism, which is what makes it switchable off
     *   without a branch.
     */
    fun of(
        plan: GatheringPlan,
        graph: ItemSourceGraph,
        model: UnitCostModel,
        threshold: Double,
    ): List<TagAssumption> {
        if (threshold <= 0.0) return emptyList()

        // Unpriceable targets are *skipped*, not fatal. Written the other way first — one
        // unpriced target aborted the whole thing — and the mechanism then silently never fired
        // on any real plan, because a 554-target import reliably contains at least one item the
        // model has no finite route for. Every unit test passed, because a hand-built fixture is
        // all-priceable by construction; only the running app showed it.
        //
        // Skipping is also the safe direction. A smaller denominator makes every share *larger*,
        // so an incompletely priced plan errs toward asking rather than toward assuming.
        val planMinutes = plan.targets.sumOf { target ->
            val unit = model.cost[target.item.id] ?: 0.0
            if (unit >= UnitCostModel.UNREACHABLE) 0.0 else unit * target.amount
        }
        // No denominator at all is different from a small one: "0% of nothing" is unknown, not
        // small, and assuming into it would answer every question in the plan at once.
        if (planMinutes <= 0.0) return emptyList()

        return plan.nodes.values
            .filter { it.status == PlanNodeStatus.OPEN_TAG && it.item is MinecraftTag }
            .mapNotNull { node ->
                val tag = node.item as MinecraftTag
                val unit = model.cost[tag.id] ?: return@mapNotNull null
                if (unit >= UnitCostModel.UNREACHABLE) return@mapNotNull null

                val share = (unit * node.quantity) / planMinutes
                if (share >= threshold) return@mapNotNull null

                val member = TagMemberRanking.recommended(graph, tag.content, costModel = model)
                    ?: return@mapNotNull null

                TagAssumption(
                    tagId = tag.id,
                    tagName = tag.name,
                    memberId = member.id,
                    memberName = member.name,
                    shareOfPlan = share,
                )
            }
            .sortedBy { it.tagId }
    }
}
