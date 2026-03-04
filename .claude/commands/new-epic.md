---
name: new-epic
description: Scaffolds a new epic by planning with the architect agent, optionally involving the product-owner for feature epics, then passing to the tech-lead for feasibility review. Creates an epic spec file in tasks/epics/ and a Linear epic with child issues on approval.
---

# New Epic

Orchestrates the full epic planning flow for a large, cross-cutting change.

## Flow

```
1. You provide a description (can be rough or detailed)
2. architect agent determines if PO involvement is needed
3a. Technical epic: architect drafts directly
3b. Feature epic: PO defines user value first, architect plans on top of that
4. You iterate — fill gaps, correct assumptions, answer questions
5. When satisfied: trigger tech-lead review with "ready for tech review"
6. Tech-lead returns verdict
7. On approval: epic spec written to tasks/epics/, Linear epic + child issues created
8. Next steps presented — how to proceed with /new-task for each child feature
```

## Step 1 — Classify the epic

Use the `architect` agent to read the description and classify it:

- **Technical epic**: database migrations, architecture rewrites, caching, performance work, internal refactors with no user-visible change → skip PO, go straight to Step 3a
- **Feature epic**: new UI, changed workflows, new user-visible features → involve PO in Step 3b
- **Uncertain**: architect asks the user directly before proceeding

## Step 2 — PO involvement (feature epics only)

Use the `product-owner` agent to define:
- Which personas are affected and how
- What the user value is
- Phase compliance — does this belong in Phase 1, 2, or 3?
- Any scope concerns from a product perspective

The PO produces a brief (not a full spec — just the product framing). The architect uses this as input.

## Step 3 — Architect drafts epic spec

Use the `architect` agent to produce the full epic spec:
- Current state vs. target state
- Migration strategy
- Decomposition into child features with sequencing and dependency mapping
- Deferred/out of scope items named explicitly
- Risks and unknowns
- Next steps for the user

Mark every gap with `[GAP: what's missing]` and every assumption with `[ASSUMPTION: what was assumed]`.

Flag scope creep explicitly: if something doesn't belong in this epic, name it and set it aside. Do not include it in the decomposition.

## Step 4 — Iterate

Present the draft to the user. Answer questions, fill gaps, correct assumptions. Re-draft as needed.

The architect should ask focused follow-up questions for any remaining `[GAP]` items. One question at a time if multiple gaps exist.

## Step 5 — Tech lead review

When the user says "ready for tech review" (or equivalent), use the `tech-lead` agent to review the finalised epic spec.

The tech lead reviews for:
- Architectural feasibility of the overall plan
- Module boundary compliance across all child features
- Migration strategy safety
- Restricted area isolation (scorer, graph structure, auth, destructive migrations)
- Whether the sequencing is correct and safe

The tech lead must return one of four verdicts:

- **Approved** — proceed
- **Changes recommended** — implementable as-is but specific improvements suggested; user decides whether to incorporate
- **Changes required** — must be resolved before any implementation begins; re-review after changes
- **No** — not feasible, or fundamentally wrong approach; explain why and what would need to be true for reconsideration

## Step 6 — Finalise

On approval (either "Approved" or "Changes recommended" where user accepts):

1. Write the epic spec to `tasks/epics/{slug}.md` where slug is kebab-case epic name
2. Use the `/linear` skill to:
    - Create a Linear epic in workspace evegul, team Mcorg
    - Title: epic name
    - Description: epic goal + migration strategy summary + link to spec file
    - Create child Linear issues for each child feature (stub only — full spec comes via /new-task)
    - Link all child issues to the epic
3. Add the Linear epic ID and child issue IDs to the epic spec front matter
4. Present the **Next steps** section from the epic spec prominently — this is the user's guide to proceeding
5. Confirm all actions to the user

On "Changes required": do not write the file or create Linear items. Return to iteration.

On "No": do not write the file or create Linear items. Summarise the architect's and tech lead's reasoning. Suggest what would need to change for this to be reconsidered.

## After approval — how to proceed

Each child feature in the decomposition becomes its own `/new-task` run. The architect's Next steps section tells the user:
- Which child feature to start with
- Which can be parallelised
- What preparation is needed before starting

The user runs `/new-task` for each child feature independently, in the order the architect specified. The child feature's `/new-task` run will reference the epic spec for context.