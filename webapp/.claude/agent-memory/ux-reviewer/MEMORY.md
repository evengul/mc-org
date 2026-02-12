# UX Reviewer Agent Memory

## Project Design System
- CSS variables defined in `src/main/resources/static/styles/root.css`
- Three themes: overworld (light default), nether (dark crimson/gold), end (dark purple/endstone)
- Button variants: `btn--action`, `btn--neutral`, `btn--danger`, `btn--ghost`
- Sizes: `btn--sm`, `btn--lg`
- Existing component CSS files: chip, button, button-states, common, form-controls, progress, etc.
- No `badge` or `badge--*` CSS classes exist in the project (notification-badge is different)
- `subtle` class exists in common.css for subdued text
- Chip component (`chip`, `chip--success`, etc.) is the correct alternative to badges

## Path Selector Component (Reviewed 2026-02-11)
- Located at: `src/main/kotlin/app/mcorg/presentation/templated/project/ResourcePathSelector.kt`
- CSS at: `src/main/resources/static/styles/pages/project-page.css` (lines 435-627)
- Pipeline at: `src/main/kotlin/app/mcorg/pipeline/resources/SelectResourcePathPipeline.kt`
- Key issues found: missing badge CSS, HTMX swap mismatch on expand, selected state color problems
- See `ux-review-resource-path-selector.md` for full details

## HTMX Patterns Observed
- Path selector uses `hxPut` for selection, `hxGet` for expand
- "Select Resource Path" ghost button in ResourcesTab triggers initial load into `#found-paths-for-gathering-{id}`
- Expand button targets `closest .path-requirement` with `innerHTML` swap -- PROBLEMATIC
- Save selection (`handleSaveResourcePath`) replaces entire `#path-selector-{gatheringId}` with `outerHTML`

## Common UX Patterns
- Resource gathering items show progress bars, action buttons (+1, +64, +1728, +3456), delete button
- Ghost buttons used for secondary actions, icon buttons for destructive actions
- `searchableSelect` component used for item selection (autocomplete dropdown)
- `emptyState` component used for empty lists
- `chipComponent` used for status indicators

## File Locations
- Page CSS: `src/main/resources/static/styles/pages/`
- Component CSS: `src/main/resources/static/styles/components/`
- Root CSS vars: `src/main/resources/static/styles/root.css`
- Templates: `src/main/kotlin/app/mcorg/presentation/templated/`
- Handlers: `src/main/kotlin/app/mcorg/presentation/handler/`
