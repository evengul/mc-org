# mc-engine Scoring Audit — Path Suggestion Selection

**Scope:** `PathSuggestionScorer`, `PathSuggestionService.suggestPath`, `ProductionBranch.getScore`,
`ProductionTree.pruneRecursively` / `deduplicated`, and `ItemSourceGraphQueries.buildProductionTree`.

**Status: contains FLAGGED scoring-logic changes that require human sign-off before commit** (see
[Flagged code changes](#flagged-code-changes)). All changes are uncommitted in the working tree.

---

## Methodology

1. Read all engine sources, the `SourceType` base scores, and the existing test fixtures.
2. Curated 12 targets where a knowledgeable player has a defensible "best" acquisition path, chosen to
   cover every scorer factor: base score, `efficiencyBonus`, `worldProductionsBonus`,
   `recipeThresholdBonus`, `circularBlockLootPenalty`, requirement/depth penalties — plus the
   prune-vs-suggest divergence and dedup/visited interactions.
3. Hand-computed factor-by-factor scores for every branch of every target *before* writing tests,
   then encoded the expectations in `ScoringSelectionTest.kt` using the real pipeline
   (`ResourceSource` → `ItemSourceGraphBuilder` → `findProductionChain` → `deduplicated()` →
   `suggestPath`), with realistic loot-table filenames (`blocks/<x>.json`, `entities/<x>.json`) as
   produced by `ParseFilesRecursivelyStep.getRelativePath`.
4. Root-caused every mismatch; implemented minimal fixes for the two systematic circularity bugs
   (flagged), documented the rest as known limitations with `@Disabled` tests or behaviour-pinning tests.

Scorer constants for reference: base scores BLOCK/ENTITY 100, CRAFTING 95, STONECUTTING 90,
SMELTING 85, BLASTING/BARTER 80, CHEST 60; `EFFICIENCY_WEIGHT` 20/ratio-point,
`PRODUCTION_BONUS` 30/ingredient, `RECIPE_THRESHOLD_BONUS` 50, `CIRCULAR_BLOCK_LOOT_PENALTY` 200,
`REQUIREMENT_PENALTY` 10/ingredient, `DEPTH_PENALTY` 5/level.

---

## Summary table

| # | Item (context) | Expected best | Actual (before fixes) | Match? | Cause |
|---|----------------|---------------|----------------------|--------|-------|
| 1 | `oak_planks` | craft from log | craft from log | YES | efficiency bonus (+60) dominates |
| 2 | `stick` (small qty) | craft from planks | **witch entity drop** | **NO** | ENTITY base 100 > recipe 95 net; no drop-rate dimension. `@Disabled` test; no fix applied |
| 3 | `stick` (256) | craft from planks | craft from planks | YES | `recipeThresholdBonus` +50 fixes it at bulk |
| 4 | `torch` | craft coal+stick | craft | YES | own-block loot penalized, chest loot low |
| 5 | `chest` | craft from planks tag | craft | YES | own-block loot penalized; tag resolves to member recipe |
| 6 | `glass` | smelt sand | smelt sand | YES | own-block (silk-touch) loot penalized; sand tag left unresolved |
| 7 | `beacon` | craft | craft | YES | circular block loot penalty working as designed |
| 8 | `redstone_lamp` | craft | craft | YES | as above; dust-from-glowstone-block not falsely penalized after fix |
| 9 | `diamond` | mine diamond ore | **craft from diamond block (240); mining scored −100** | **NO (inverted)** | storage-block double bug — FIXED (flagged) |
| 10 | `iron_ingot` (small) | smelt raw iron | **craft from iron block (240)** | **NO** | same double bug — FIXED (flagged) |
| 11 | `iron_ingot` (500) | smelt raw iron | craft from iron block | NO → YES after fix | threshold bonus applied to the circular recipe too |
| 12 | `coal` | mine coal ore | craft from coal block; mining −100 | NO | same as diamond — FIXED (flagged) |
| 13 | `arrow` (no farms) | skeleton farm (defensible) | skeleton entity | YES* | pinned as current behaviour; crafting also defensible |
| 14 | `arrow` (flint/stick/feather farmed) | craft | craft | YES | `worldProductionsBonus` +90 flips it |

\* both answers defensible; test pins behaviour rather than asserting superiority.

**Score: 8/12 matched before fixes; 11/12 after the flagged fixes; 1 remains a documented,
`@Disabled` known mis-rank (stick at small quantities).**

---

## Discrepancy 1 — the storage-block double bug (FIXED, flagged)

Every "9× storage block" resource (diamond, coal, redstone, lapis, emerald, iron, gold, …) was
mis-ranked in **both directions simultaneously**:

**Before, for `diamond` (required 10):**

| Branch | base | eff | reqs | depth | circ | total |
|---|---|---|---|---|---|---|
| mine `blocks/diamond_ore.json` | 100 | 0 | 0 | 0 | **−200** | **−100** |
| craft 1 `diamond_block` → 9 | 95 | **+160** | −10 | −5 | 0 | **+240** |
| chest `buried_treasure` | 60 | 0 | 0 | 0 | 0 | 60 |

Suggested path: *"craft diamonds from a diamond block"* — circular nonsense; mining scored below chest loot.

Two independent root causes:

1. **`circularBlockLootPenalty` keyed only on (type==BLOCK, any recipe sibling).** The unpacking
   recipe `diamond_block → 9 diamonds` counts as a "recipe alternative", so *mining natural diamond
   ore* was treated as "break what you crafted" and hit with −200. The heuristic's stated intent
   (beacon, redstone lamp) inverted for every ore with a storage block.
2. **Circular crafting is invisible to the scorer, while its 9× output triggers an uncapped
   `efficiencyBonus` of +160** (ratio 9.0). The cycle `diamond → diamond_block → diamond` is cut by
   the visited-set/dedup placeholder, so the branch *looks* like a cheap shallow recipe and wins.

**Fix (in working tree, FLAGGED):** `PathSuggestionScorer.score` gained an optional
`selfItemIds: Set<String>` parameter (target item id + tag-member ids, computed by
`PathSuggestionScorer.selfItemIds(tree)` and passed by `PathSuggestionService`):

- The block-loot penalty now only fires when the broken block **is the item itself**
  (loot filename stem == self item name: `blocks/beacon.json` ↔ `minecraft:beacon`). Mining
  `blocks/diamond_ore.json` for `minecraft:diamond` is no longer circular. Tag-member ids are
  included so tag nodes (which inherit member sources) still penalize a member's own block loot.
- New `CIRCULAR_CRAFTING_PENALTY = 200`: a recipe branch whose ingredient subtree **contains the
  target item itself** (via `findSubtreeForItem`) is penalized. This catches both
  `diamond ← diamond_block ← diamond` and `iron_ingot ← iron_nugget ← iron_ingot`.
- With `selfItemIds` empty (all pre-existing call sites/tests) behaviour is **unchanged** — the
  legacy blanket penalty applies.

**After, for `diamond`:** mine ore 100, unpack block 40 (95+160−15−200), chest 60 → mining wins.
Verified for diamond, coal, iron (smelting 70 > blasting 65 > chest 60 > unpack 40 > nuggets <0).

False-positive review of the filename-stem heuristic: `oak_log` (stem==self but no recipe sibling →
unpenalized, correct); `stone`/`sandstone`/`glowstone` (natural blocks with recipes → penalized;
the recipe path is an acceptable default); `melon_slice` from `blocks/melon.json` (stem≠self →
unpenalized, correct). Containment-penalty review: vanilla recipes essentially never contain their
own output in a legitimate supply chain except pack/unpack cycles — checked stick, torch, chest,
piston, hopper, dispenser, comparator, anvil, netherite; the one debatable hit is
`gold_ingot ← 9 gold_nuggets` (nuggets also drop from piglins), where smelting raw gold is at least
as good a default.

Considered and rejected: capping `efficiencyBonus` (a cap of +60 still leaves the unpack recipe
at 140, beating smelting at 70 — insufficient alone, so left uncapped; could be revisited).

## Discrepancy 2 — entity loot outranks cheap recipes at small quantities (NOT fixed, documented)

`stick` (required 10): witch entity drop = 100; crafting = 95 + 20 (eff 2.0) − 10 (1 req) − 10
(depth 2) = **95**. Suggestion: *"kill witches for sticks."* Above the `recipeThreshold` the +50
recipe bonus fixes it (145 vs 100), so this only mis-ranks small quantities.

This is systemic: witches drop redstone/glowstone dust/sugar/sticks, zombies drop iron, and ENTITY's
base 100 beats CRAFTING 95 whenever efficiency−penalties nets ≤ +5. **No code fix applied** because
the deciding factor a player uses — drop rate / farmability — does not exist in the graph, and any
blanket "entity penalized when recipe exists" adjustment breaks correct cases:

- `leather`: cow entity (100) **should** beat the `rabbit_hide` crafting recipe (~80). A −25 entity
  penalty would invert it.
- `iron_ingot`/`gold_ingot`: iron-golem / zombified-piglin farms are the expert-player meta — entity
  winning there is arguably *correct*.

Encoded as `@Disabled` test (`stick - small amount should be crafted from planks…`). Real options,
all needing a human decision: (a) ingest loot-table drop probabilities/rolls into edge quantities so
`producedQuantity` reflects expected yield, (b) lower ENTITY to 90–95 and accept the leather-style
regressions, (c) treat sub-threshold required amounts as mildly recipe-preferring. Option (a) is the
principled one.

## Discrepancy 3 — `getScore()` vs `score()` divergence in pruning (mitigated, flagged)

`ProductionBranch.getScore()` = `base − 10·reqCount − maxDepth` — no efficiency, no circularity, no
context. `pruneRecursively` uses it by default, so pruning can discard exactly the branch the rich
scorer would pick:

- **Beacon, prune to 1:** structural scores: block loot 100, crafting 65 → crafting pruned. The
  survivor then has *no recipe sibling*, so the circular penalty disappears and `suggestPath`
  confidently returns "break the placed beacon". Demonstrated by
  `default pruning discards the branch the suggestion scorer would pick`.
- **iron_ingot, prune to 2 (realistic default):** structural scores rank the two *circular* recipes
  (84, 84) above smelting (74) — pruning keeps only the garbage. Demonstrated by
  `default pruning of iron_ingot keeps both circular recipes and drops smelting`.

**Fix (flagged):** `pruneRecursively`'s scorer lambda now receives the parent tree
(`(ProductionTree, ProductionBranch) -> Int`; default unchanged, no external callers exist), and
`PathSuggestionScorer.treeAwareScorer(context)` provides a drop-in scorer that applies the full
contextual scoring (sibling-recipe detection + selfItemIds) per level. Tests verify tree-aware
pruning preserves the suggestion-best branch in both cases. **Recommendation:** any future caller
that prunes before suggesting must use `treeAwareScorer`.

## Discrepancy 4 — cross-branch truncation leaves winning branches unresolved (NOT fixed, documented)

`buildProductionTree` shares one `visited` set across **sibling** branches (it is never removed after
recursion), and `deduplicated()` mirrors this with empty placeholders keyed by item id. Consequence:
an ingredient first reached inside branch A keeps its subtree only there; if `suggestPath` picks
branch B, B's copy of that ingredient is an empty leaf → left without a source **even though the
graph has one**, and `ProductionPath.isComplete()` still returns `true` (a leaf with `source == null`
counts as complete). Demonstrated by
`KNOWN LIMITATION - an ingredient first seen in a losing sibling branch is unresolved…`.

Not fixed because the correct fix is architectural, not local: either (a) make `visited` path-scoped
(backtracking) so siblings each get full subtrees — risks significant tree blow-up on the real graph
at `maxDepth=10` — or (b) make `suggestPath` placeholder-aware by resolving repeated item ids
against a side-table of first occurrences. (b) is the cheaper, recommended direction. Also note
`deduplicated()` has a latent oddity: it caches `seenItems[itemId] = subtree` but never reads the
cache (always returns an empty placeholder), so the map is effectively a `Set`.

## Other observations (no action)

- **Ties are order-dependent:** `maxByOrNull` keeps the first of equal-scored branches, and branch
  order comes from `LinkedHashMap` insertion order in the graph builder — i.e. extraction file order.
  Real tie example: `redstone` via mining redstone ore (100) vs witch drop (100). Deterministic per
  build, but arbitrary. A stable tiebreak (e.g. prefer recipes, then lexical key) would make
  suggestions reproducible across data refreshes.
- `recipeThresholdBonus` applies to *any* recipe, including circular ones (now moot since the
  circular penalty dwarfs it) and regardless of how far above threshold the amount is (step
  function, not scaled). Fine at current magnitudes.
- `efficiencyBonus` magnitudes are sane in the legitimate range (planks 4.0 → +60, sticks 2.0 → +20,
  slabs 2.0 → +20); only the pack/unpack cycles produced pathological ratios, now handled by the
  circularity penalty rather than a cap.
- `worldProductionsBonus` (+30/ingredient) behaves well: it flips arrow from "skeleton farm" to
  "craft" when all three ingredients are farmed (66+90=156 > 100) and is decisive between same-type
  recipes, without overwhelming the circularity penalties.
- The hardcoded wither loot uses filename `"wither.json"` (no directory); the stem heuristic handles
  both `blocks/x.json` and bare `x.json` shapes.

---

## Flagged code changes

**All three changes below alter or extend scoring/ranking behaviour and require human sign-off
(per project rules for `PathSuggestionScorer`). They are uncommitted.**

1. **`PathSuggestionScorer.kt`** — added optional `selfItemIds` param to `score(...)`; refined
   `circularBlockLootPenalty` (self-block filename match instead of blanket); added
   `circularCraftingPenalty` (ingredient chain contains target, −200); added public helpers
   `selfItemIds(tree)` and `treeAwareScorer(context)`. Legacy behaviour preserved when
   `selfItemIds` is empty — all 9 pre-existing scorer tests pass unchanged.
2. **`PathSuggestionService.kt`** — passes `selfItemIds` to the scorer (this is what activates the
   refined behaviour in production paths). All 10 pre-existing service tests pass unchanged.
3. **`ItemSourceGraphQueries.kt`** — `pruneRecursively` scorer lambda signature widened to
   `(ProductionTree, ProductionBranch) -> Int` (default behaviour identical; no callers outside
   mc-engine existed).

If change 1/2 is rejected, the following tests must be disabled: `diamond - mining…`,
`iron_ingot - smelting…` (both), `coal - mining…`, the two `scorer - …circular…` tests, the
`scorer - natural ore…` test, and `tree-aware pruning…`/`default pruning of iron_ingot…`.

## Tests added

`webapp/mc-engine/src/test/kotlin/app/mcorg/engine/services/ScoringSelectionTest.kt` — 22 tests:
14 curated-target selection tests (assertions on selected `sourceKey`s and recursive structure),
3 prune-divergence tests, 1 cross-branch-truncation documentation test, 4 direct scorer tests for
the refined circularity handling, 1 `@Disabled` known mis-rank (stick, small quantity).

## Verification

```
cd webapp && JAVA_HOME=<jdk-25> mvn test -pl mc-engine
Tests run: 87, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

(The 1 skip is the intentionally `@Disabled` stick test.) Full-reactor `mvn clean compile` also
passes (exit 0). Note: the repo's JDK-25 migration means tests must run under JDK 25
(`~/.jdks/jdk-25.0.3+9` locally); the default `java` on PATH is still 21 and fails with a
class-file-version error.

## Confidence assessment

- **High confidence:** the storage-block double bug (reproduced through the real
  builder→query→dedup→suggest pipeline; factor table verified against code), the prune divergence,
  and the cross-branch truncation — all are demonstrated by passing tests, not inferred.
- **Medium confidence:** the filename-stem self-block heuristic. It is correct for every case
  examined (beacon, lamp, glass, planks, ores, melon, logs) but couples scoring to loot-table
  filename conventions; if extraction ever changes filename shapes, the heuristic degrades back to
  "no penalty" (fails open — suggestions get the beacon bug back, nothing crashes). A more robust
  long-term signal would be an explicit produced-item↔block-identity edge from mc-data.
- **Medium confidence:** the circular-crafting containment check. `findSubtreeForItem` is a full
  recursive scan per branch per self-id; fine at suggestion-time tree sizes (post-dedup), but worth
  watching if ever applied to unpruned, undeduplicated trees.
- **Honest caveats:** fixtures are realistic but hand-built; the real extracted graph may contain
  additional sources per item (notably entity drops — see Discrepancy 2 — and stonecutting variants)
  that shift rankings. The entity issue is the main remaining known mis-rank and needs a
  data-model decision (drop rates), not a weight tweak.

---

## Resolution (2026-06-10, MCO-195)

This audit was written against the tree-based suggestion engine
(`ProductionTree` / `PathSuggestionScorer` / `PathSuggestionService`). The
quantity-aware planner (`app.mcorg.engine.plan`, MCO-190..MCO-194) supersedes
that machinery, and the audit's uncommitted worktree changes were **not merged**
— each finding was re-implemented or dissolved in the new design:

| Audit finding | Fate in the new engine |
|---|---|
| Storage-block double bug (Discrepancy 1) | Re-implemented structurally. `PlanSelector` rejects a candidate whose ingredient chain cannot complete without the item being resolved (no −200 penalty needed), and the self-block filename heuristic lives on in `SelectionScorer.isSelfBlockLoot` — used both as a scoring penalty and to stop own-block loot from grounding an unpack chain. |
| Entity-vs-recipe at small quantities (Discrepancy 2) | Unchanged known limitation. `@Disabled` test carried over in `CuratedSelectionTest`; the principled fix is drop-rate ingestion (MCO-196). |
| Prune/score divergence (Discrepancy 3) | Dissolved. The two-scorer split no longer exists: selection has exactly one scorer (`SelectionScorer`) and no pruning stage. The `treeAwareScorer` patch was not carried over. |
| Cross-branch truncation (Discrepancy 4) | Dissolved by the DAG model — one accumulating node per item, no sibling-visited placeholders. Verified by `CuratedSelectionTest."an ingredient shared between competing recipes is fully resolved in the winning chain"`. |
| Order-dependent ties | Fixed: selection ties break recipe-first then by source key; activity ordering ties break by group then item id. |

The curated cases (planks, sticks, torch, chest, glass, beacon, redstone lamp,
diamond, iron, coal, arrow, truncation) are ported to
`mc-engine/src/test/kotlin/app/mcorg/engine/plan/CuratedSelectionTest.kt`.
One deliberate behaviour change: tag ingredients are no longer silently
resolved to a member recipe — they surface as OPEN_TAG for the user to pick
(chest/glass cases assert this).
