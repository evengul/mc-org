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
3b. Feature epic: PO produces product framing block, architect plans on top of that
4. You iterate — fill gaps, correct assumptions, answer questions
5. When satisfied: trigger tech-lead review with "ready for tech review"
6. Tech-lead returns verdict
7. On approval: epic spec written to tasks/epics/, Linear epic + child issues created
8. Next steps presented — how to proceed with /new-task for each child feature
```

## Step 1 — Classify the epic

Use the `architect` agent to read the description and classify it:

- **Technical epic**: no user-visible change when complete → skip PO, go straight to Step 3a
- **Feature epic**: user will see, feel, or experience anything differently → involve PO in Step 3b
- **Uncertain**: architect asks the user directly before proceeding

## Step 2 — PO involvement (feature epics only)

Use the `product-owner` agent in **epic context (Context B)**. It must produce the full product framing block as defined in its output format — not a summary, not prose.

The framing block covers: personas affected, user value, phase compliance, scope concerns, IA conflicts, and a PO verdict.

Do not proceed to Step 3 until the PO has returned the complete framing block. If the PO returns a summary or prose instead of the structured block, instruct it to re-output using Context B format explicitly. The architect reads this block directly as input — it cannot work from a paraphrase.

## Step 3 — Architect drafts epic spec

Use the `architect` agent to produce the full epic spec. For feature epics, pass the PO's complete framing block as input — do not summarise it.

The architect produces:
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