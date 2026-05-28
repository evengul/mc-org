---
name: new-task
description: Scaffolds a new feature spec by drafting it with the product-owner agent, iterating with you, then passing to the tech-lead for feasibility review. Creates a spec file in tasks/ and a Linear issue on approval.
---

# New Task

Orchestrates the full spec creation flow for a new feature or task.

## Flow

```
1. You provide a rough description (can be one line or a full brief)
2. product-owner agent drafts the full spec (Context A — Spec draft)
3. You iterate — fill gaps, correct assumptions, answer questions
4. When satisfied: trigger tech-lead review with "ready for tech review"
5. tech-lead returns verdict (approved / changes recommended / changes required / no)
6. On approval: spec file written to tasks/, Linear issue created
```

## Step 1 — Draft spec

Use the `product-owner` agent in **Context A (Spec draft)**. It must produce the complete spec document — not a summary, not a verdict, not prose. Every section of the spec format must be filled in.

If the product-owner returns anything other than a complete spec document, instruct it to re-output using Context A format explicitly.

The draft must:
- Fill every spec section (summary, user value, scope, behaviour, technical approach, sub-tasks, acceptance criteria, out of scope)
- Mark every gap with `[GAP: what's missing]`
- Mark every assumption with `[ASSUMPTION: what was assumed]`
- Flag scope concerns inline with `[SCOPE: reason]`
- Flag size concerns with `[SIZE: consider splitting — reason]` if the feature likely spans more than 3–5 implementation tasks

## Step 2 — Iterate

Present the full spec draft to the user. Answer questions, fill gaps, correct assumptions as directed. Re-draft sections as needed. For any remaining `[GAP]` items after the first draft, ask one focused follow-up question at a time.

## Step 3 — Tech lead review

When the user says "ready for tech review" (or equivalent), use the `tech-lead` agent to review the finalised spec.

The tech lead must return one of four verdicts:

- **Approved** — proceed
- **Changes recommended** — implementable as-is but specific improvements suggested; user decides whether to incorporate before proceeding
- **Changes required** — must be resolved before implementation begins; re-review after changes
- **No** — feature is not feasible, conflicts with architecture, or is fundamentally wrong for the codebase; explain why and what would need to be true for it to be reconsidered

## Step 4 — Finalise

On approval (either "Approved" or "Changes recommended" where user accepts):

1. Write the spec file to `tasks/{slug}.md` where slug is kebab-case feature name
2. Use the `/linear` skill to create a Linear issue:
   - Workspace: evegul, team: Mcorg
   - Title: spec title
   - Description: spec summary + link to spec file path
   - If the spec contains sub-tasks, create them as sub-issues
3. Add the Linear issue ID to the spec file front matter
4. Confirm both actions to the user

On "Changes required": do not write the file or create the Linear issue. Return to iteration.

On "No": do not write the file or create the Linear issue. Summarise the tech lead's reasoning and suggest next steps.