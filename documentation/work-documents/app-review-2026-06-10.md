# MC-ORG Consolidated App Review — 2026-06-10

All findings below have been adversarially verified and severity-calibrated. Three review tracks: **DB/performance** (web + engine), **UX / design-system / accessibility**, and **architecture / code-health**.

---

## Executive Summary

**Total confirmed findings: 48**

### By severity

| Severity | Count |
|----------|-------|
| High     | 3     |
| Medium   | 11    |
| Low      | 28    |
| Info     | 6     |

### By dimension

| Dimension                        | Findings |
|----------------------------------|----------|
| DB/perf — web                    | 8        |
| DB/perf — engine                 | 8        |
| UX — templates                   | 11       |
| UX — mobile / a11y               | 14       |
| Architecture — boundaries        | 9        |
| Architecture — code health       | 14       |

(Some findings overlap across tracks — e.g. the breadcrumb world-name bug, the `status-dot` colour-only state, and the inline-`style=` violations were each surfaced independently by two reviewers. Counts above reflect raw findings per track; the de-duplicated body groups them.)

### Highest-impact items (top 5)

1. **`getSourcesForItem` is O(S) per call on a ~3,000-source graph, invoked across the tree build** — `ItemSourceGraph.kt:136`. A missing producer reverse-index forces a full scan on a hot, user-facing path-suggestion path. *(high)*
2. **`StoreMinecraftDataStep` silently discards all three sub-step failures** and unconditionally returns success — `StoreServerDataSteps.kt:22`. A DB error commits partial Minecraft data into the tables that feed the engine graph, while logging "Successfully stored". *(high)*
3. **`status-dot` conveys "source set/unset" by green-vs-grey colour alone** in the plan resource table — `project-detail.css:277`. WCAG 1.4.1 violation directly affecting the primary (red-green colour-blind) user. *(high)*
4. **`extractProductionRates` always stores 0** — `ImportIdeaPipeline.kt:173`. The `amountValue.value` is never read, so every imported production item persists `rate_per_hour = 0`. *(medium, data-correctness)*
5. **Authorization performed inside a pipeline Step (`ValidateIdeaOwnerStep`)** — `DeleteIdeaPipeline.kt:29`. Violates the explicit "auth in plugins, never in pipelines" critical rule; a correct in-repo pattern already exists in `DraftHandlers.kt`. *(medium)*

---

## DB / Performance — mc-engine

The engine graph and traversal are the product core. These are the highest-value performance findings.

### HIGH — `getSourcesForItem` scans the entire `sourceToItemEdges` map (O(S) per call)
- **File:** `mc-engine/.../model/ItemSourceGraph.kt:136`
- **Problem:** `getSourcesForItem` filters every source entry (`S` ≈ 3,000+ in production) on each call. It is invoked per expanded node during `buildProductionTree` (the user-facing path-suggestion path), and also per-item in `findLeafItems` and `analyzeGraph`, giving repeated O(I·S) scans. A `visited` set bounds re-expansion, so it is not unbounded blowup, but the absolute cost on the production graph is substantial.
- **Fix:** Add an `itemToProducerEdges: Map<ItemNode, Set<SourceNode>>` (item → sources that *produce* it) built in the Builder, mirroring the existing consumer index `itemToSourceEdges`. Rewrite `getSourcesForItem` to return `itemToProducerEdges[itemNode] ?: emptySet()` — O(1). The Builder's `addSourceToItemEdge` already has the data to populate it.

### MEDIUM — `getDepth()` recomputes the full subtree depth on every call
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:418`
- **Problem:** `ProductionTree.getDepth()` is unmemoized and descends the whole subtree. It is called from `ProductionBranch.getScore()` (line 455) and `PathSuggestionScorer.score()` (line 69), both of which run at every recursion level during scoring/pruning — so a deep subtree's depth is recomputed once per ancestor. Bounded by `maxDepth=10` and `maxBranchesPerLevel=2`, so it is a per-request CPU cost, not a scaling cliff.
- **Fix:** Make depth a `val depth: Int by lazy { ... }` on the immutable data class (annotate `@Transient` since the class is `@Serializable`). Collapses repeated O(T) traversals to O(1) after first access.

### MEDIUM — `ProductionBranch.getScore()` calls `getDepth()` on all `requiredItems` in the scoring hot path
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:454`
- **Problem:** `getScore()` is the default `scorer` lambda in `pruneRecursively` and is also used in `getNBestBranches`; each invocation triggers an unmemoized full-subtree `getDepth()`. (Note: the writeup's claim that each scoring call traverses twice is inaccurate — `getScore()` and `PathSuggestionScorer.score()` are distinct code paths, not a doubled traversal in one call.)
- **Fix:** Same as above — memoizing `getDepth()` eliminates the redundant recomputation. Trees are shallow (small crafting depth, branching capped at 2), so impact is modest but the fix is free.

### MEDIUM — `findShortestPath` BFS scans all sources per dequeued node (O(S) per step)
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:261`
- **Problem:** `graph.getAllSources().filter { source -> graph.getRequiredItems(source).contains(current) }` runs once per dequeued `ItemNode`, with a fresh O(S) allocation each step — making the BFS O(V·S) instead of O(V+E).
- **Fix:** The private `itemToSourceEdges` map already holds item → sources-that-require-it. Expose `getSourcesRequiringItem(item): Set<SourceNode>` backed by that map and use `itemToSourceEdges[current] ?: emptySet()`. (Note: the existing public `getSourcesForItem` returns *producers*, not consumers, so it cannot be reused here.)

### LOW — `pruneRecursively` scores all branches then discards all but `maxBranchesPerLevel`
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:360`
- **Problem:** All branches are scored before `.take(maxBranchesPerLevel)`. Top-k inherently requires scoring all candidates, so the real cost is the non-memoized `getDepth()` inside `scorer`, not the discard itself. `pruneRecursively` has no production callers (only its own recursion + tests).
- **Fix:** Memoize depth (above). Optionally replace the full sort with a min-heap top-k selection when `B >> maxBranchesPerLevel` — minor.

### LOW — `buildProductionTree` shares one `visited` set across sibling branches
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:78`
- **Problem:** `visited` is global-DFS and never removed on backtracking. An ingredient shared across branches (e.g. sticks in both `diamond_sword` and `diamond_pickaxe`) is truncated to an empty `ProductionTree` on the second encounter, understating its depth and biasing scoring. The live `PathSuggestionService` path operates on a `deduplicated()` tree which already collapses shared items, so impact is muted for that consumer; raw `findProductionChain`/`pruneRecursively` consumers see the bias.
- **Fix:** Either use per-path `visited` sets with a global depth/cost cap, or accept the truncation and document it explicitly as a deliberate trade-off. (Correctness-adjacent — flag for human decision before changing traversal semantics.)

### LOW — `reconstructPath` uses `list.add(0, …)` prepend (O(D²))
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:301`
- **Problem:** Prepending into an `ArrayList` shifts all elements each step. Path lengths are small (BFS depth, capped near 10), so absolute cost is negligible.
- **Fix:** Use `ArrayDeque.addFirst` (O(1)) or accumulate in reverse and `.reversed()` once.

### LOW — `getItemNodesByStringId` linear-scans all item nodes per call
- **File:** `mc-engine/.../model/ItemSourceGraph.kt:198`
- **Problem:** `itemNodes.values.filter { it.itemId == itemId }` is O(I). Its only caller is the string overload of `findProductionChain`, which currently has no production callers (test-only). The "typical entry point from mc-web" claim in the original finding is unsupported.
- **Fix:** Build a `Map<String, Set<ItemNode>>` index in `Builder.build()` for O(1) lookup.

---

## DB / Performance — mc-web

All web DB findings are low/info: legitimate efficiency cleanups, none on a hot or scaling path.

### LOW — Three separate queries per progress-update request
- **File:** `mc-web/.../resources/UpdateItemTaskRequirementsPipeline.kt:100` (also `SetCollectedValuePipeline.kt:82`)
- **Problem:** `GetUpdatedTaskCountsStep` fires `GetResourceGatheringItemStep` + `CountTotalResourcesRequiredInProjectWithItemIdStep` + `CountCollectedResourcesInProjectWithItemIdStep` sequentially per +/- click. The two SUM queries each use an identical correlated subquery. Fixed 3 queries per action over indexed columns on a small table — not N+1.
- **Fix:** Collapse the two SUM queries into one (`SELECT SUM(required), SUM(collected) ... WHERE project_id = (...)`).

### LOW — `insertNodeTree` issues one INSERT per node
- **File:** `mc-web/.../resources/ResourceGatheringPlanSteps.kt:61`
- **Problem:** One `INSERT … RETURNING id` per plan-tree node, N round-trips inside the transaction. Low-frequency write path (explicit plan save), trees bounded by realistic crafting depth.
- **Fix:** Pre-order traversal + `DatabaseSteps.batchUpdate` (already used elsewhere), or a `WITH RECURSIVE` insert. The `RETURNING id` parent-linkage is handled by a recursive CTE or pre-assigned temp IDs + `unnest()`.

### LOW — `GetItemsInVersionRangeStep` fetches entire table then filters in Kotlin
- **File:** `mc-web/.../idea/commonsteps/GetItemsInVersionRangeStep.kt:46`
- **Problem:** Bounded ranges fetch every row (all versions) and discard non-matches via `if (!input.contains(version)) continue`. Table is a few thousand short rows; cost is mostly redundant row decoding, not query weight.
- **Fix:** Push filtering into SQL. Note the naive `WHERE version IN (...)` from bounds is not cleanly implementable (open-ended bounds; snapshot/release ordering) — the real fix subqueries/joins against the version table or passes a resolved version set.

### LOW — `GetManagedWorldsStep` uses two correlated COUNT subqueries per row
- **File:** `mc-web/.../admin/commonsteps/GetManagedWorldsStep.kt:16`
- **Problem:** Per-`world` correlated COUNTs for `projects` and `world_members`. Paginated (default 10 rows) on an admin-only path; both `world_id` columns indexed, so each COUNT is a cheap index scan.
- **Fix:** Replace with `LEFT JOIN` aggregates grouped by `world.id` — a style/consistency improvement; perf delta on 10 rows is negligible.

### LOW — `IdeaSqlBuilder` interpolates `$key` into JSONB path expressions
- **File:** `mc-web/.../idea/IdeaSqlBuilder.kt:78`
- **Problem:** `i.category_data->'$key'->>'value'` string-interpolates a JSONB key into raw SQL (also lines 86, 103, 108, 115). Guarded at runtime by `require(key in ALLOWED_CATEGORY_FIELD_KEYS)`; the allowlist is derived from compile-time schemas. Not exploitable today — a latent/defense-in-depth concern with no regression test protecting the guard.
- **Fix:** A JSONB path key cannot be bound with `?`. Keep the allowlist (correct mitigation), document it explicitly, and add a test asserting `buildJsonbCondition` throws `IllegalArgumentException` on an arbitrary key.

### INFO — `SearchIdeasPipeline` builds SQL then wraps in `SafeSQL.select` (dual-bypass note)
- **File:** `mc-web/.../idea/SearchIdeasPipeline.kt:38`
- **Problem:** The assembled string (including the interpolated WHERE fragment) is passed to `SafeSQL.select`, which validates the final string rather than preventing a dangerous one. Acceptable given the allowlist guard; no defect.
- **Fix:** Document the convention so future contributors don't add unguarded interpolation into `SqlWhereClause`.

### INFO — `GetAllIdeasStep` is dead code
- **File:** `mc-web/.../idea/commonsteps/GetAllIdeasStep.kt:11`
- **Problem:** Defined but never called anywhere; `SearchIdeasStep` (paginated) is the live path. If ever called it would return every idea unbounded.
- **Fix:** Remove it.

### INFO — Test suite fails locally with `UnsupportedClassVersionError`
- **File:** `mc-web` (environment)
- **Problem:** Local runner is JDK 21 (class file version 65); project migrated to JDK 25 (class file version 69) in commit `2fa5481`. Pre-existing environment gap, not a code defect.
- **Fix:** No code change. Use JDK 25 locally; CI (`temurin:25`) is authoritative. (See duplicate notes under arch-health and arch-boundaries.)

---

## UX — Templates

### HIGH — `status-dot` conveys state by colour alone (colour-blind safety)
- **File:** `mc-web/.../static/styles/pages/project-detail.css:277`
- **Problem:** In the plan-view resource table, `.status-dot--unset` (grey) vs `.status-dot--set` (green) is the *sole* indicator of whether a source is configured. The dot (`ProjectDetailPage.kt:371-373`) is an empty span — no text, icon, `title`, `aria-label`, or sr-only label, and the column header is empty. WCAG 1.4.1 violation directly affecting the documented red-green colour-blind primary user. State is recoverable via the detail panel, so it is an at-a-glance scanning loss, not a hard break.
- **Fix:** Add a non-colour signal: swap the dot for an icon glyph (`·` vs `✓`), or add a visually-hidden `<span class="sr-only">Source set</span>` / `Source not set`. Prefer the blue↔yellow (lapis/amber) axis per the design spec. (The other `status-dot` usages in `ResourceDetailPanel.kt` are decorative — they sit beside text labels and are not affected.)

### MEDIUM — Toggle active state does not use `--accent` fill
- **File:** `mc-web/.../static/styles/components/toggle.css:34`
- **Problem:** `.toggle__btn--active` uses a raised-surface look (`background: var(--bg-surface)` + box-shadow) instead of the spec's lapis fill. The design system (`docs-product`) explicitly requires "Active: `--accent` background, `--on-accent` text" for the Plan/Execute toggle; the sibling `tabs.css` already implements it correctly. The toggle is the outlier on the most-used interactive control.
- **Fix:** `.toggle__btn--active { background: var(--accent); color: var(--on-accent); }` and remove the box-shadow. Keep inactive as `color: var(--text-muted); background: transparent`.

### MEDIUM — Breadcrumb second segment uses project name instead of world name
- **File:** `mc-web/.../dsl/pages/ProjectDetailPage.kt:64`
- **Problem:** Breadcrumb renders `Worlds › [Project Name] › [Project Name]` — the project name is duplicated and the world name absent. IA spec mandates `Worlds › [World Name] › [Project Name]`. The middle link's href is correct (`/worlds/{worldId}/projects`), so navigation works; only the label is wrong. `Project` has no `worldName` field. (Also surfaced under mobile-a11y at line 65 as low — same bug.)
- **Fix:** Pass `worldName` (or a `World`) into `projectDetailPage` (the sibling `ProjectListPage` already receives a `World`) and use it as the second breadcrumb label. Note the handler currently holds only `worldId`, so it must fetch the world name.

### MEDIUM — Mobile header missing hamburger control
- **File:** `mc-web/.../dsl/Navigation.kt:101`
- **Problem:** The mobile default header renders a centred world name + right-aligned profile/settings, with nothing on the left. IA spec is `☰ [World Name] ⚙`. No hamburger/drawer exists anywhere in the codebase. The breadcrumb nav lives in `app-header__desktop` (`display:none` below 768px), so mobile has neither breadcrumb nor hamburger — a real navigation gap.
- **Fix:** Add the hamburger control and a navigation drawer, or track explicitly as a Phase 1 IA gap.

### LOW — Inline `style="text-align: center;"` in `CreateIdeaVersionFields.kt`
- **File:** `mc-web/.../idea/createwizard/CreateIdeaVersionFields.kt:72`
- **Problem:** Violates the "NEVER inline `style=`" critical rule. Cosmetic, no functional impact.
- **Fix:** Add a CSS class (e.g. `.wizard-version__unbounded`) and apply via `classes = setOf(...)`.

### LOW — Inline `style="display: none;"` in `SearchableSelect.kt`
- **File:** `mc-web/.../dsl/SearchableSelect.kt:192`
- **Problem:** Inline style on `searchable-select__clear`. `.is-hidden` exists in `design-tokens.css:145`. (No JS currently toggles this element — confirm any visibility-toggle JS uses `classList`, not `.style.display`, when migrating.)
- **Fix:** Apply `.is-hidden` and toggle via class.

### LOW — Hardcoded hex in `btn.css` (`#fff`, `#cc4545`)
- **File:** `mc-web/.../static/styles/components/btn.css:65` (and `:70`)
- **Problem:** `.btn--danger` uses `color: #fff` and `:hover { background: #cc4545 }` — raw hex, inconsistent with the token-based siblings. Note `#fff` ≠ `--on-accent` (`#FCF7EA`, cream), and `#cc4545` is a *lighter* red than `--red` (`#A6321F`), so it's a hover-brighten, not a darken.
- **Fix:** Replace `#fff` with `var(--on-accent)`. For hover, add a `--red-hover` token (needs a human design decision — the current value isn't a simple darken).

### LOW — Hardcoded hex + undefined token in `task-list.css`
- **File:** `mc-web/.../static/styles/components/task-list.css:86`
- **Problem:** `color: var(--danger, #e53e3e)` — `--danger` is undefined, so the off-brand fallback `#e53e3e` always applies (vs the project's `--red` `#A6321F`). Renders red, just the wrong shade. (Duplicated under mobile-a11y.)
- **Fix:** Replace with `var(--red)`.

### LOW — Hardcoded `#fff` in `idea-hub.css`
- **File:** `mc-web/.../static/styles/pages/idea-hub.css:255`
- **Problem:** `.ideas-pagination__page--current { background: var(--accent); color: #fff; }` — should be cream `--on-accent`. Minor visual inconsistency vs other accent-filled components.
- **Fix:** Replace `#fff` with `var(--on-accent)`.

### LOW — Hardcoded `#fff` in `idea-wizard.css` (two occurrences)
- **File:** `mc-web/.../static/styles/pages/idea-wizard.css:51` (and `:100`)
- **Problem:** Active/hover progress-step number uses `color: #fff` on `var(--accent)` fill; should be `--on-accent`. No a11y impact (both legible on lapis).
- **Fix:** Replace both `#fff` with `var(--on-accent)`.

### LOW — Idea Hub empty state uses `<p>` instead of `<h2>`
- **File:** `mc-web/.../idea/IdeaList.kt:20`
- **Problem:** `p("empty-state__heading")` where the shared `emptyState` DSL helper uses `h2("empty-state__heading")` — a semantics inconsistency. (The "missing class" framing in the raw finding is wrong: the class is applied and defined in `idea-hub.css:219`; the `body`/`actions` children are optional and legitimately omitted for a filter-empty notice.)
- **Fix:** Use `h2("empty-state__heading")`, or call the `emptyState(heading, …)` DSL helper for consistency.

---

## UX — Mobile & Accessibility

### MEDIUM — Plan-view resource table `<td>` cells missing `data-label`
- **File:** `mc-web/.../dsl/pages/ProjectDetailPage.kt:372`
- **Problem:** The five `td` cells (status/item/qty/source/action) carry no `data-label`. `data-table.css` mobile reflow (≤768px) renders column headers via `td::before { content: attr(data-label) }`, so the stacked cards show blank labels. `AdminPage.kt` (the only other `data-table` consumer) sets `data-label` on every cell — this is a genuine deviation. Degrades gracefully (content still shows).
- **Fix:** Add `data-label="Item"`, `data-label="Qty"`, `data-label="Source"` to the text columns; leave status/action empty (icon-only).

### MEDIUM — No consistent keyboard `:focus-visible` indicator on custom buttons/links
- **File:** `mc-web/.../static/styles/components/form.css:28`
- **Problem:** Inputs replace the native outline with a low-contrast `--accent-muted` ring (`outline: none` also in `modal.css:73`, `world-card.css:31`, `resource-search.css:26`, `task-list.css:120`, `admin-page.css:29`). `.btn`, `.toggle__btn`, card links etc. define no `:focus-visible` ring and rely on inconsistent UA defaults. (The title's "suppressed globally for all `:focus`" claim is overstated — each `outline: none` is scoped to specific inputs, not a global `*:focus`, so native focus still applies to most custom controls.)
- **Fix:** Add a global `*:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }` in `design-tokens.css`/`reset.css` and remove the bare `:focus` outline suppressions that lack an equivalent visible indicator.

### MEDIUM — Alert toast container has no ARIA live region
- **File:** `mc-web/.../dsl/Alert.kt:14`
- **Problem:** `alertContainer()` renders a bare `ul` with only an id. HTMX injects alert `<li>` via `afterbegin` OOB swaps (`ErrorHandler.kt:167`), but with no `aria-live`/`role`, screen readers don't announce them. WCAG 4.1.3 (Status Messages, AA). Bounded impact — failures also surface via HTTP status / form errors.
- **Fix:** Add `attributes["aria-live"] = "polite"` (or `role="status"`/`role="log"`) to the `ul`.

### MEDIUM — Generic `modal()` / `modalForm()` lack `aria-labelledby` / `aria-describedby`
- **File:** `mc-web/.../dsl/Modal.kt:84`
- **Problem:** `confirmDeleteModal` sets these correctly; the generic helpers don't. `modalForm()` ships live in Create World (`WorldListPage.kt:125`) and Create Project (`ProjectListPage.kt:217`). Native `<dialog>` carries implicit dialog semantics, so this is a missing-accessible-name enhancement (WCAG 4.1.2), not a hard break. (`modal()` itself is dead code.)
- **Fix:** Give the heading `id = "$id-title"` and set `dialog attributes["aria-labelledby"] = "$id-title"`.

### MEDIUM — Resource counter buttons have no `aria-label`
- **File:** `mc-web/.../dsl/ResourceRow.kt:57`
- **Problem:** Six counter buttons (`-1728 … +1728`) per row announce as bare signed numbers ("+1", "-64") with no item context. Across many rows a screen-reader user gets no per-control indication of which item. WCAG 2.4.6 name-in-context. `itemName` is in scope.
- **Fix:** `attributes["aria-label"] = "${if (amount > 0) "Add" else "Remove"} ${abs(amount)} $itemName"`.

### MEDIUM — `task-row__delete-btn` tap target too small on mobile
- **File:** `mc-web/.../static/styles/components/task-list.css:62`
- **Problem:** `padding: 0 var(--space-1)` (4px) with no `min-width`/`min-height` yields a ~12–15px target — well below the system's 36px/44px minimums (enforced on `resource-row__counter-btn`, `btn`, cards). A destructive action, made visible on touch via an `@media (hover:none)` opacity override.
- **Fix:** Add `min-width: 36px; min-height: 36px` to the base rule and `min-width: 44px; min-height: 44px` inside `@media (max-width: 768px)`.

### LOW — Inline `style="width: …%"` on progress-bar fills (three locations)
- **File:** `mc-web/.../dsl/ProgressBar.kt:13` (also `ResourceRow.kt:33`, `WorldCard.kt:29`)
- **Problem:** Inline `style=` violates the critical rule. A11y is correct here (all three carry `role="progressbar"` + `aria-value*`). Dynamic per-render width is the most defensible inline-style case; no functional impact.
- **Fix:** Set a CSS custom property via `data-*` attribute + a CSS rule, for rule compliance.

### LOW — Inline `style="color: …"` on `IconComponent`
- **File:** `mc-web/.../dsl/Icons.kt:75`
- **Problem:** `attributes["style"] = "color: ${color.cssVar};"`. All `cssVar` values are currently `"inherit"` (dead-token follow-up), so this is a no-op today, but still an inline-style violation. `icon--colored` class already exists for non-default colours.
- **Fix:** Remove the inline style; apply a modifier class if explicit colour control is ever needed.

### LOW — Hardcoded hex on `btn--danger` hover (mobile-a11y view)
- **File:** `mc-web/.../static/styles/components/btn.css:70` — *(same as the templates-track btn.css finding; see there.)*

### LOW — Hardcoded `--danger` fallback on task delete hover (mobile-a11y view)
- **File:** `mc-web/.../static/styles/components/task-list.css:86` — *(same as the templates-track task-list.css finding.)*

### LOW — Breadcrumb middle segment shows project name (mobile-a11y view)
- **File:** `mc-web/.../dsl/pages/ProjectDetailPage.kt:65` — *(same bug as the MEDIUM templates-track breadcrumb finding; label-only, no a11y barrier.)*

### LOW — `status-dot` colour-only state (mobile-a11y view)
- **File:** `mc-web/.../static/styles/pages/project-detail.css:277`
- **Problem:** Same dot as the HIGH templates finding. From the mobile-a11y lens it is lower risk: green-vs-grey is a *luminance* difference (distinguishable under red-green CVD), and the adjacent source column renders the same set/unset state as text ("Manual gather"/project name vs "--"), so the dot is redundant decoration there.
- **Fix:** Optional sr-only label or filled-vs-ring shape. (The templates-track HIGH rating stands because at-a-glance scanning still benefits.)

### LOW — `pending-invitations.css` hardcodes `8px` radius
- **File:** `mc-web/.../static/styles/components/pending-invitations.css:13` (and `:52`)
- **Problem:** `border-radius: 8px` (the only `8px` in the styles tree) vs `--radius` (6px); line 52 uses `6px` directly. Visual deviation + token bypass.
- **Fix:** Replace both with `var(--radius)`.

### LOW — `idea-hub.css` references undefined `--text-heading`
- **File:** `mc-web/.../static/styles/pages/idea-hub.css:309`
- **Problem:** `.idea-detail__name { font-size: var(--text-heading); }` — token undefined and no fallback, so the value resolves to `inherit` and the `<h1>` idea-detail title renders at body size (`--text-base` 15px). Cosmetic visual-hierarchy regression; h1 semantics intact.
- **Fix:** Define `--text-heading` (e.g. 24px) in `design-tokens.css`, or hardcode a size until added.

---

## Architecture — Boundaries

### MEDIUM — Authorization inside a pipeline Step: `ValidateIdeaOwnerStep`
- **File:** `mc-web/.../idea/single/DeleteIdeaPipeline.kt:29`
- **Problem:** A `Step` performs ownership authorization (`… WHERE id = ? AND created_by = ?` → `NotAuthorized`), violating "Authorization via Ktor plugins at route level — NEVER inside pipelines." The delete route installs only `IdeaParamPlugin` (no ownership plugin), so this Step is the only gate. The same module's `handleRevertIdeaToDraft` (`DraftHandlers.kt:405-417`) does the identical check at handler level with an explicit "not inside the pipeline step" comment — the correct in-repo pattern. The check is present and correct (not a security hole) — purely a layering violation. The anti-pattern recurs across the repo (see next finding and `ValidateWorldMemberRole`, `RemoveWorldMemberPipeline`).
- **Fix:** Move the ownership check to the handler (before `handlePipeline`) or a route-scoped plugin, reusing the existing `GetIdeaStep`.

### LOW — Authorization inside a pipeline Step: `ValidateInviterPermissionsStep`
- **File:** `mc-web/.../world/settings/invitations/HandleCreateInvitation.kt:163`
- **Problem:** `if (userRole == null || userRole.level > Role.ADMIN.level) → NotAuthorized` inside a Step. (The finding's title misnames it "ValidateSenderRoleStep".) The route is *already* behind `WorldAdminPlugin` (nested under `/settings`), which enforces the identical ADMIN-or-higher rule — so this is redundant duplication, not an auth gap; no exposure.
- **Fix:** Remove the in-Step check; rely on `WorldAdminPlugin`.

### LOW — Inline `style=` in `SearchableSelect.kt`
- **File:** `mc-web/.../dsl/SearchableSelect.kt:192` — *(same as UX-templates finding; `display: none` on the clear button.)*

### MEDIUM — Inline `style=` in `CreateIdeaVersionFields.kt`
- **File:** `mc-web/.../idea/createwizard/CreateIdeaVersionFields.kt:72`
- **Problem:** `style = "text-align: center;"` — explicit critical-rule violation. (Severity is kept medium under the boundaries track for the rule breach, though the visual impact is purely cosmetic; the UX track rates the same line low.)
- **Fix:** Introduce/use a `text-center` utility class.

### LOW — Inline `style=` in `GetCreateCategoryFieldsFragmentPipeline.kt` (3 occurrences)
- **File:** `mc-web/.../idea/createfragments/GetCreateCategoryFieldsFragmentPipeline.kt:18` (and `:41`, `:53`)
- **Problem:** Three `style = "text-align: center;"` on `p("subtle")` helper text. Cosmetic rule violation. (See also the de-duplicated arch-health roll-up of all five inline-style sites.)
- **Fix:** Replace with a CSS utility class.

### LOW — mc-engine service files declare `package app.mcorg.domain.services`
- **File:** `mc-engine/.../service/ItemSourceGraphQueries.kt:1`
- **Problem:** All four service files (`ItemSourceGraphQueries`, `ItemSourceGraphBuilder`, `PathSuggestionScorer`, `PathSuggestionService`) live in `mc-engine` but declare a `domain` package, contradicting mc-engine's own `CLAUDE.md` ("Package: app.mcorg.engine.*"). Kotlin doesn't enforce package==dir, so it compiles; no circular dependency (mc-domain still doesn't depend on mc-engine). Namespace-confusion hygiene only.
- **Fix:** Rename the package to `app.mcorg.engine.service` (blast radius confined to mc-engine + its tests).

### LOW — Business logic on mc-domain models
- **File:** `mc-domain/.../model/world/Roadmap.kt:27`
- **Problem:** `RoadmapNode` has `isReadyToStart()`/`isInProgress()`/`isCompleted()`; `InviteStatus.Pending` has `accept`/`decline`/`expire`/`cancel` state transitions; `PerformanceTestData` has a `require(mspt >= 0.0)` invariant. mc-domain's `CLAUDE.md` says "No business logic". These are borderline — pure lookups with no side effects or framework deps, not module-boundary breaches. The `InviteStatus` state machine is the strongest "logic" case (and is dead code in mc-web).
- **Fix:** Developer decision — keep for convenience (boundaries intact) or move to mc-web helpers. Flag, don't auto-fix.

### INFO — Test suite cannot execute (JDK 21 vs 25)
- **File:** `webapp/pom.xml:1` — *(same environment issue; see DB-perf INFO. No action; CI uses temurin:25.)*

---

## Architecture — Code Health

### HIGH — `StoreMinecraftDataStep` silently discards sub-step failures
- **File:** `mc-web/.../minecraftfiles/StoreServerDataSteps.kt:22`
- **Problem:** `StoreMinecraftVersionStep`, `StoreMinecraftItemDataStep`, and `StoreResourceSourcesStep` `.process()` results are all discarded; the method unconditionally returns `Result.success()` (line 28). Because the inner update steps convert exceptions to returned `Result.failure` (not throws), a DB error does *not* abort the transaction — it commits partial data into `minecraft_items`/`minecraft_tag`/`resource_source` (the tables feeding the engine graph), invalidates the cache, and logs "Successfully stored". Admin-only, rare ingestion path; inserts are partly defended (`ON CONFLICT DO NOTHING`, delete-then-insert in one tx), so triggering needs an actual DB error.
- **Fix:** Propagate each result with `.bind()` in a `pipelineResult` block, or check each explicitly and return early on failure.

### MEDIUM — `extractProductionRates` always stores 0
- **File:** `mc-web/.../project/ImportIdeaPipeline.kt:173`
- **Problem:** `productionRates[itemId] = (productionRates[itemId] ?: 0)` — `amountValue.value` (an `IntValue` in scope via smart-cast) is never read, so every imported production item persists `rate_per_hour = 0` (the value flows through `ValidateItemIdsStep` → `INSERT INTO project_productions`). Single-feature data-correctness defect; the TODO is about aggregating multiple entries, not whether the value is read.
- **Fix:** `productionRates[itemId] = (productionRates[itemId] ?: 0) + amountValue.value`.

### MEDIUM — `DynamicOptionsConfig.cache` is an unsynchronized `MutableMap`
- **File:** `mc-web/.../domain/idea/schema/DynamicOptionsConfig.kt:9`
- **Problem:** Module-level `mutableMapOf()` (a `LinkedHashMap`) mutated via an unsynchronized check-then-act in `resolve()`, called from concurrent Ktor request handlers (idea-creation validation, draft wizard). Realistic failure modes: lost writes, transient empty/stale reads, rare corrupted resize — not the `ConcurrentModificationException` the finding claims (no iteration). Narrow write window (write-once per tiny version-range key space).
- **Fix:** Use `ConcurrentHashMap` as the interim fix, or implement the intended DB-backed cache.

### MEDIUM — `runBlocking` nested inside a suspend context
- **File:** `mc-web/.../domain/idea/schema/DynamicOptionsConfig.kt:20`
- **Problem:** `resolve()` (non-suspend) wraps a suspend DB call in `runBlocking`; it's reached from `ValidateIdeaCategoryDataStep.validateValue` (`:108`) which is *itself* in a `runBlocking` inside a `suspend process`. So a `runBlocking` nests inside another on a Netty worker, blocking the thread instead of suspending. Low-frequency write path (form submission) and `resolve()` caches per range, so the realistic risk is added latency under contention, not a likely outage — "thread starvation" is somewhat overstated.
- **Fix:** Make `resolve()` / `validateValue` `suspend`; the planned DB-backed Caffeine cache also resolves it.

### LOW — Three (five total) inline `style=` attributes in templates
- **File:** `mc-web/.../idea/createfragments/GetCreateCategoryFieldsFragmentPipeline.kt:18` (+`:41`,`:53`), `CreateIdeaVersionFields.kt:72`, `SearchableSelect.kt:192`
- **Problem:** De-duplicated roll-up of all five inline-`style=` sites (the only ones in the presentation tree). Cosmetic critical-rule violations; nothing breaks.
- **Fix:** Author a `text--center` utility class (none exists yet) for the centering cases; use a class/`data-*` for the `display:none` toggle.

### LOW — `GetManagedUsersStep` hardcodes `globalRole = Role.MEMBER`
- **File:** `mc-web/.../admin/commonsteps/GetManagedUsersStep.kt:40`
- **Problem:** Every managed user displays MEMBER, so the admin page's role-dependent ban/promote controls are wrong for BANNED/OWNER users. (The finding's claim that there's no `global_role` column is **false** — `V2_11_0__create_global_user_roles_table.sql` exists and is queried elsewhere; the fix is to JOIN it.) Impact is limited: the admin action endpoints (`/role`, `/ban`, delete) are unimplemented stubs, so the controls perform nothing — cosmetic on an incomplete, admin-gated page.
- **Fix:** Read the role from the existing `global_user_roles` table.

### LOW — `GetIdeaForImportStep` second query runs outside the transaction connection
- **File:** `mc-web/.../project/ImportIdeaPipeline.kt:140`
- **Problem:** The `idea_item_requirements` query omits `transactionConnection = connection` (its sibling at line 133 passes it), acquiring a separate pooled connection. But the enclosing transaction is read-only (two SELECTs, no writes) and the pool is `READ COMMITTED`, so there is no uncommitted data and no consistent-snapshot guarantee was ever provided — the "see uncommitted data" framing doesn't apply. Real cost: minor transient pool pressure.
- **Fix:** Pass `transactionConnection = connection` (or drop the pointless transaction wrapper).

### LOW — Stub route bodies registered but never implemented (IdeaHandler)
- **File:** `mc-web/.../handler/IdeaHandler.kt:119`
- **Problem:** Four routes (`/public`, `/archive`, comment update, `/like`) have empty bodies → empty 200 responses, misleading for callers. (The finding's "no-auth enforcement" claim is **false** — `AuthPlugin` is installed at the root routing scope, so all four are behind authentication; no bypass.)
- **Fix:** Implement, return 501 with an HTML fragment, or remove the registrations until ready.

### LOW — Unreachable `HX-Current-URL "/admin"` branch in `DeleteWorldPipeline`
- **File:** `mc-web/.../world/DeleteWorldPipeline.kt:19`
- **Problem:** The admin-delete branch (`respondEmptyHtml()`) can never be reached — `handleDeleteWorld` is only wired under `/worlds/{worldId}/settings` (WorldOwnerPlugin), and the admin delete route (`AdminHandler.kt:40`) is an empty stub. Self-documented dead code.
- **Fix:** Track in Linear; wire the admin route or remove the dead branch.

### LOW — `ResolveDynamicOptionsStep` MOBS stub / ENCHANTMENTS NotFound
- **File:** `mc-web/.../idea/commonsteps/ResolveDynamicOptionsStep.kt:30`
- **Problem:** `MOBS` returns a hardcoded 3-item list; `ENCHANTMENTS` falls through to `DatabaseError.NotFound`. Both enum branches are live in the binary but currently unreachable (only `DynamicOptionsConfig.items()`/ITEMS is wired) and failures are swallowed to an empty list. Latent trap if a schema ever wires MOBS (silent fake data).
- **Fix:** Track in Linear; consider removing the unused enum values + stub branches.

### LOW — `GetItemsInVersionRangeStep` filters versions in Kotlin not SQL
- **File:** `mc-web/.../idea/commonsteps/GetItemsInVersionRangeStep.kt:22` — *(same as the DB-perf web finding; bounded ranges fetch all rows then filter in-JVM.)*
- **Fix:** Push filtering into SQL — but note `MinecraftVersion` ordering is non-lexicographic (custom snapshot/release `compareTo`) and the column is text, so a naive `WHERE version >= ? AND version <= ?` would mis-order; needs a normalized sortable representation first.

### LOW — `GetCategoryFiltersFragmentPipeline` calls `getFilterableFields()` twice
- **File:** `mc-web/.../idea/GetCategoryFiltersFragmentPipeline.kt:26`
- **Problem:** Called at line 26 (`.forEach`) and line 31 (`.isEmpty()`); `getFilterableFields()` re-filters and re-allocates each call (no memoization). Cold path, no correctness impact.
- **Fix:** `val fields = schema.getFilterableFields()` once, reuse.

### LOW — Per-call logger instantiation in `StoreServerDataSteps`
- **File:** `mc-web/.../minecraftfiles/StoreServerDataSteps.kt:17`
- **Problem:** Five per-call `LoggerFactory.getLogger(...)` assignments (lines 17, 40, 55, 207, 211) inside method/lambda/catch bodies — an SLF4J map lookup per call, inconsistent with the class-level pattern elsewhere (e.g. `GetItemsInVersionRangeStep`). Negligible perf (ingestion path); value is consistency.
- **Fix:** Move to companion-object / top-level `private val logger`.

### INFO — Test suite blocked by JDK 21 vs 25 class-file mismatch
- **File:** `mc-domain/.../MinecraftVersionTest.kt:1` — *(same environment issue. Install temurin-25 / set JAVA_HOME; CI authoritative. No code change.)*

---

## Suggested priority order

1. **`StoreMinecraftDataStep` silent failure** (`StoreServerDataSteps.kt:22`) — data-integrity bug feeding the engine graph; small, mechanical fix with high downside if it fires.
2. **`getSourcesForItem` O(S) scan** (`ItemSourceGraph.kt:136`) — biggest real performance win on the product-core path; add the producer reverse-index. *(Touches mc-engine — flag graph-shape addition for review per `mc-engine/CLAUDE.md`.)*
3. **`status-dot` colour-only state** (`project-detail.css:277`) — accessibility for the primary user; add an icon/sr-only label.
4. **`extractProductionRates` stores 0** (`ImportIdeaPipeline.kt:173`) — one-line data-correctness fix.
5. **Auth-in-pipeline `ValidateIdeaOwnerStep`** (`DeleteIdeaPipeline.kt:29`) — move to handler/plugin using the existing in-repo pattern; also addresses the broader recurring anti-pattern.
6. **Engine `getDepth` memoization + `findShortestPath` reverse-index** (`ItemSourceGraphQueries.kt:418`, `:261`) — cheap, low-risk perf cleanups on the same core. *(mc-engine — flag.)*
7. **Mobile/a11y batch:** `data-label` cells, global `:focus-visible`, alert `aria-live`, modal `aria-labelledby`, counter-button `aria-label`, delete-button tap target.
8. **Toggle `--accent` fill + breadcrumb world-name** — visible design/IA fidelity fixes. *(Scoring/`--accent` is a token swap, not a `PathSuggestionScorer` change.)*
9. **Concurrency + `runBlocking`** in `DynamicOptionsConfig` — `ConcurrentHashMap` + `suspend` refactor.
10. **Low/info hygiene sweep:** inline-style removals, hardcoded-hex → tokens, undefined-token fixes, dead code (`GetAllIdeasStep`, stub routes, unreachable branches), package rename, allowlist test, logger hoisting. Batch into a single cleanup PR.
11. **Toolchain (info):** install temurin-25 locally to restore local test execution — no code change.
