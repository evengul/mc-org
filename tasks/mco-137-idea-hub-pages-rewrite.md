---
linear-issue: MCO-137
status: approved
phase: 1
created: 2026-03-12
depends:
  - MCO-134  # Feature 5: Navigation chrome + page shell
---

# Feature 8 — Idea Hub Pages Rewrite

## Summary

Rewrite the Idea Hub list page (`/ideas`) and detail page (`/ideas/:ideaId`) using the new
DSL template architecture and design tokens. Replace the session-based creation wizard with
database-backed drafts. The Idea Hub is the community surface replacing Discord as the
submission channel — ideas are submitted by users with the `idea_creator` role and browsed
by everyone.

Split into two child issues with no shared implementation tasks:
- **MCO-151** — Browse + pagination (list page, detail page, filter sidebar)
- **MCO-152** — Creation rewrite (session → database-backed drafts)

## User Value

- **Casual player / Worker:** Browse and filter ideas, import to world. Server-side
  pagination prevents loading hundreds of ideas at once.
- **Technical player (submitter):** Submit ideas without losing state — drafts persist
  across sessions and are resumable at any time. Multiple drafts can be worked on in
  parallel over months.
- **Everyone:** Idea Hub looks and feels consistent with the rest of the rewritten app.

## Scope

### In scope — MCO-137a (Browse + pagination)

- Rewrite `/ideas` list page: `pageShell()`, idea cards, filter sidebar, HTMX
  search/filtering, server-side pagination (20 per page)
- Rewrite `/ideas/:ideaId` detail page: header, badges, description, favourite button,
  import button, rating distribution, comments
- Breadcrumbs: "Ideas" on list, "Ideas > [Idea Name]" on detail
- Preserve all existing HTMX filter/search behaviour (category radio → dynamic fields,
  debounced text search, difficulty, rating, version, clear all)

### In scope — MCO-137b (Creation rewrite)

- New `idea_drafts` table (Flyway migration)
- Draft CRUD pipeline steps + publish step with JSONB deserialization
- Rewrite creation wizard templates with new DSL
- Draft list UI at `GET /ideas/create`
- Delete session-based wizard code

### Explicitly out of scope

- Version mismatch modal on import → deferred to project detail feature
- Default tasks on import → deferred
- Moderation/approval workflow (submitter → notification → approve/reject) → Phase 2
- Public/archive toggles → Phase 2
- Comment like/unlike, comment editing → Phase 2
- Idea search by produced resource → defer
- Mobile filter drawer animation (JS-dependent) → per epic risk table
- Litematica upload UX redesign → not the bottleneck

## Behaviour

### Idea List (`/ideas`)

**Layout:** `pageShell()` with `appHeader()`. Two-column on desktop: filter sidebar (fixed
width, `--bg-surface`) left, card grid right.

**Mobile layout:** Single column. Filter hidden behind a "Filters" toggle button
(`.btn--ghost`). Tapping opens the filter as a full-width overlay panel (`position: fixed`,
`--bg-surface`, full height, scrollable). Tapping outside or a "Close" button dismisses.
CSS-only preferred; minimal inline JS acceptable for backdrop dismiss.

**Filter sidebar:**
- Text search input — `hx-get="/ideas/search"`, `hx-trigger="input delay:400ms"`,
  `hx-target="#ideas-list-container"`, `hx-include="#idea-filter-form"`
- Category radio buttons — on change fires HTMX GET to `/ideas/filters/{category}`,
  swaps `#category-filters` with category-specific fields from `IdeaCategorySchemas`
- Difficulty checkboxes, minimum rating, MC version
- "Clear All" ghost button — resets form, fires search with empty params

**Card grid + pagination (wrapped in `#ideas-list-container`):**

The HTMX swap target is `#ideas-list-container`, which contains both the card grid and
pagination controls. Both `handleGetIdeas` (full page) and `handleSearchIdeas` (fragment)
produce this container; the full page wraps it in `pageShell()`.

- 20 ideas per page (LIMIT/OFFSET)
- Each card: idea name (IBM Plex Mono, `--text-heading`), author + date (`--text-muted`,
  `--text-xs`), truncated description (2 lines, Inter `--text-sm`), category badge,
  difficulty badge, version range badge, favourite count, star rating average
- Card hover: `--bg-raised`, border `#3a4060`; links to `/ideas/:ideaId`
- Empty state: `.empty-state` "No ideas match your filters"

**Pagination controls** (inside `#ideas-list-container`, below card grid):
- Previous / page numbers / Next
- Preserve all active filter params + `page=N` in page links
- HTMX: page links swap `#ideas-list-container`

### Idea Detail (`/ideas/:ideaId`)

**Layout:** `pageShell()`, breadcrumb "Ideas > [Idea Name]".

**Header:** idea name, author + date + star rating, category/difficulty/version/label
badges, full description.

**Actions:**
- Favourite toggle button — existing HTMX PUT preserved
- "Import to World" button → existing `handleGetSelectWorldForIdeaImportFragment` (world
  selector dropdown). On version match: redirect to project execute view. Version mismatch
  modal deferred.

**Rating distribution:** bar rows (5★ → 1★) with percentage fill, average score, count.

**Comments:** add form (textarea + optional 1–5 star radio + submit), comment list with
delete (own only, existing HTMX DELETE preserved).

### Creation Flow — MCO-137b

**Draft model:**
```sql
idea_drafts(
  id            SERIAL PRIMARY KEY,
  user_id       INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  data          JSONB NOT NULL DEFAULT '{}',
  current_stage VARCHAR(50) NOT NULL DEFAULT 'BASIC_INFO',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
)
-- No unique constraint on user_id — multiple drafts per user supported
```

**`GET /ideas/create`:**
- User has drafts → show draft list page: cards with name (from `data->>'name'` or
  "Untitled"), last updated date, stage progress, "Continue editing" / "Publish" / "Discard"
- "New Draft" button always visible at top
- No drafts → create new draft row, redirect to wizard at BASIC_INFO

**Routes (all under `IdeaCreatorPlugin` auth scope):**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ideas/create` | Draft list or redirect to new draft |
| POST | `/ideas/create` | Create new draft, redirect to wizard |
| GET | `/ideas/drafts/:draftId/edit` | Load wizard at draft's `current_stage` |
| POST | `/ideas/drafts/:draftId/stage` | Save stage data, advance stage, return next fragment |
| DELETE | `/ideas/drafts/:draftId` | Discard draft (ownership check: user_id must match) |
| POST | `/ideas/drafts/:draftId/publish` | Validate + create idea + delete draft + redirect |

The new `route("/drafts")` block in `IdeaHandler.kt` must install `IdeaCreatorPlugin`,
parallel to the existing `/create` block.

**Wizard stages:**
1. BASIC_INFO — name, description, difficulty, category
2. AUTHOR_INFO
3. VERSION_COMPATIBILITY
4. ITEM_REQUIREMENTS
5. CATEGORY_FIELDS — driven by `IdeaCategorySchemas.getSchema(category)` reading
   `data->>'category'`
6. REVIEW

Each "Next": HTMX POST saves current stage data into `data` JSONB (merge via `||` operator,
not replace), updates `current_stage`, returns next stage template fragment.

**Publish flow:**
1. `DeserializeDraftStep` — parse `data` JSONB into typed input (using `@Serializable`
   data class + `Json.decodeFromString`); fail with user-visible error if required fields
   are missing
2. `ValidateIdeaInputStep` — reuse existing validation
3. `CreateIdeaStep` — reuse existing creation
4. `CacheManager.onIdeaCreated(ideaId)` — must be called after creation (same as current
   `CreateIdeaPipeline`)
5. Delete draft row
6. Redirect to `/ideas/:ideaId`

## Technical Approach

### MCO-137a

**Templates (rewrite in-place — no new files):**
- `presentation/templated/idea/IdeasPage.kt`
- `presentation/templated/idea/IdeaList.kt`
- `presentation/templated/idea/IdeaFilter.kt`
- `presentation/templated/idea/IdeaFilterFields.kt`
- `presentation/templated/idea/IdeaPage.kt`
- `presentation/templated/idea/IdeaHeader.kt` (merge into `IdeasPage.kt` or delete)

**Pipeline changes:**
- Add `page: Int = 1` and `pageSize: Int = 20` to `IdeaSearchFilters` — pagination flows
  through the existing filter path rather than modifying `GetAllIdeasStep` (which stays
  untouched for other consumers)
- Modify `SearchIdeasStep`: append `LIMIT ? OFFSET ?`; add `COUNT(*) OVER() AS total_count`
  window function to avoid a second query; return `PaginatedResult<Idea>(items, totalCount,
  page, pageSize)` instead of `List<Idea>`
- `handleGetIdeas` (full page) and `handleSearchIdeas` (fragment) both use the paginated
  result; `handleGetIdeas` passes `page=1` by default
- `GetAllIdeasStep` is **not modified** — keep as-is; existing tests remain unaffected

**No DB migrations for MCO-137a.**

**Skills to load:** `/docs-product`, `/docs-htmx`, `/docs-development`

### MCO-137b

**Migration:** `V2_31_0__create_idea_drafts.sql`
Verify this is still the next available number at implementation time — another feature
may land a migration first.

**New pipeline steps:**
- `GetDraftsStep` — list all drafts for a user (ordered by `updated_at DESC`)
- `CreateDraftStep` — INSERT new draft row, return draft id
- `UpdateDraftStep` — merge stage data: `UPDATE idea_drafts SET data = data || $1,
  current_stage = $2, updated_at = now() WHERE id = $3 AND user_id = $4`
- `DeserializeDraftStep` — parse `data` JSONB into typed `@Serializable` input for
  validation; surface field-level errors to the user
- `PublishDraftStep` — `DeserializeDraftStep` → `ValidateIdeaInputStep` →
  `CreateIdeaStep` → `CacheManager.onIdeaCreated(ideaId)` → delete draft
- `DeleteDraftStep` — DELETE WHERE id = ? AND user_id = ? (ownership enforced in SQL)

**Templates (rewrite):**
- All files under `presentation/templated/idea/createwizard/`
- New draft list template (card pattern; edit/publish/discard per draft)

**Deletions after migration:**
- `pipeline/idea/createsession/CreateIdeaWizardSession.kt`
- `pipeline/idea/createsession/WizardSessionUtils.kt`
- `pipeline/idea/createsession/DataSource.kt`
- `pipeline/idea/createsession/WizardField.kt`

**Skills to load:** `/docs-product`, `/docs-htmx`, `/docs-development`, `/add-migration`,
`/add-step`

## Sub-tasks

### MCO-137a — Browse + Pagination

1. Rewrite list page layout — `IdeasPage.kt` with `pageShell()`, two-column desktop
   layout, mobile filter overlay
2. Rewrite idea cards — `IdeaList.kt` with new design tokens, empty state
3. Add pagination: `page`/`pageSize` to `IdeaSearchFilters`, `COUNT(*) OVER()` in
   `SearchIdeasStep`, `PaginatedResult<Idea>` return type, pagination controls template
4. Rewrite filter sidebar — `IdeaFilter.kt` + `IdeaFilterFields.kt`
5. Rewrite detail page — `IdeaPage.kt` (header, badges, actions, import, rating bars,
   comments add/delete)
6. Integration tests: filter + pagination (page 2 returns correct slice, filters
   preserved); detail page renders

### MCO-137b — Creation Rewrite

7. Migration: `idea_drafts` table
8. Draft pipeline steps: `GetDrafts`, `CreateDraft`, `UpdateDraft`, `DeleteDraft`
9. Publish pipeline: `DeserializeDraftStep` → `ValidateIdeaInputStep` → `CreateIdeaStep`
   → `CacheManager.onIdeaCreated` → delete draft
10. Draft list template + all wizard stage templates (new DSL)
11. Wire draft steps to creation routes; install `IdeaCreatorPlugin` on `/drafts` block;
    remove session-based code
12. Unit tests for each pipeline step; integration test for full draft lifecycle
    (create → fill all stages → publish → idea exists; discard → draft deleted)

## Acceptance Criteria

### MCO-137a
- [ ] `/ideas` renders via `pageShell()` — no old Component classes, no old CSS references
- [ ] Cards display: name, author, date, description (truncated 2 lines), category/
      difficulty/version badges, favourites count, rating average
- [ ] Filter sidebar: text search (debounced HTMX), category radio + dynamic HTMX fields,
      difficulty, rating, version, clear all
- [ ] Mobile: filter accessible via toggle button → full-height overlay
- [ ] Pagination: 20 ideas/page; page links preserve all active filters; HTMX swaps
      `#ideas-list-container` (cards + controls together)
- [ ] Empty state shown when no results match
- [ ] `/ideas/:ideaId` renders via `pageShell()` — no old Component classes, no old CSS
- [ ] Detail: all header fields, favourite HTMX, import → world selector, rating
      distribution bars, comments add/delete
- [ ] Breadcrumbs correct on both pages
- [ ] All existing idea-related tests pass without modification
- [ ] `GetAllIdeasStep` is unchanged
- [ ] `mvn clean compile` zero errors

### MCO-137b
- [ ] `idea_drafts` table created by migration
- [ ] Multiple drafts per user supported (no unique constraint on user_id)
- [ ] `GET /ideas/create` shows draft list when drafts exist; creates new draft and
      redirects when none
- [ ] Draft persists across server restart (verified by integration test)
- [ ] All wizard stages save to DB JSONB via `UpdateDraftStep` (merge, not replace)
- [ ] `IdeaCreatorPlugin` installed on `/drafts` route block
- [ ] Submit: `DeserializeDraftStep` → validate → create idea → `CacheManager.onIdeaCreated`
      → delete draft → redirect to `/ideas/:ideaId`
- [ ] Discard: confirmation modal → delete draft → redirect to `/ideas`
- [ ] All wizard pages use new DSL + design tokens
- [ ] Session-based wizard files deleted
- [ ] Unit tests for each new pipeline step (success + failure paths)
- [ ] Integration test covers full draft lifecycle
- [ ] `mvn clean compile` zero errors, all tests pass

## Out of Scope

| Item | Reason | Where it belongs |
|------|--------|------------------|
| Moderation/approval workflow | Phase 2 | "Idea submission moderation" feature |
| Submitter/moderator notifications | Phase 2 | Same |
| Opening submissions beyond idea_creator | Phase 2 | Same |
| Version mismatch import modal | Deferred | Project detail feature |
| Default tasks on import | Deferred | Project detail feature |
| Public/archive toggles | Stubs, not wired | Phase 2 |
| Comment like/unlike, editing | Stubs, not wired | Phase 2 |
| Idea search by produced resource | Not implemented | Defer |
| Litematica upload UX redesign | Works | Future |

## Tech Lead Review

**Verdict:** Changes recommended (incorporated above)

**Key corrections applied:**
1. Pagination via `IdeaSearchFilters` + `SearchIdeasStep` — `GetAllIdeasStep` left untouched
2. `#ideas-list-container` wraps cards + pagination as single HTMX swap target
3. `DeserializeDraftStep` added explicitly before validation in publish flow
4. `IdeaCreatorPlugin` called out explicitly for new `/drafts` route block
5. `CacheManager.onIdeaCreated(ideaId)` added to publish flow
6. Migration number to be verified at implementation time
