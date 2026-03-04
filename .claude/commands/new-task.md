---
name: new-task
description: Scaffolds a new feature spec by drafting it with the product-owner agent, iterating with you, then passing to the tech-lead for feasibility review. Creates a spec file in tasks/ and a Linear issue on approval.
---

# New Task

Orchestrates the full spec creation flow for a new feature or task.

## Flow

```
1. You provide a rough description (can be one line or a full brief)
2. product-owner agent drafts the spec, marking gaps and assumptions
3. You iterate — fill gaps, correct assumptions, answer questions
4. When satisfied: trigger tech-lead review with "ready for tech review"
5. tech-lead returns verdict (approved / changes recommended / changes required / no)
6. On approval: spec file written to tasks/, Linear issue created
```

## Step 1 — Draft spec

Use the `product-owner` subagent to draft the initial spec from the user's description.

Instruct it to:
- Produce a complete draft immediately, not ask questions first
- Mark every gap with `[GAP: what's missing]`
- Mark every assumption with `[ASSUMPTION: what was assumed]`
- Flag scope concerns inline (`[SCOPE: this may belong in Phase X]`)
- Flag size concerns if the feature likely spans more than 3–5 implementation tasks (`[SIZE: consider splitting — reason]`)

The draft uses the spec format defined below.

## Step 2 — Iterate

Present the draft to the user. Answer questions, fill gaps, correct assumptions as directed. Re-draft as needed. The product-owner agent should ask focused follow-up questions for any remaining `[GAP]` items after the first draft.

## Step 3 — Tech lead review

When the user says "ready for tech review" (or equivalent), use the `tech-lead` subagent to review the finalised spec.

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

---

## Spec Format

All specs use this structure. The product-owner agent fills this out during drafting.

```markdown
---
linear-issue: ""          # filled after Linear issue is created
status: draft             # draft | approved | in-progress | done
phase: 1                  # 1, 2, or 3 — which build phase this belongs to
created: YYYY-MM-DD
---

# [Feature Name]

## Summary
One paragraph. What this is, who it's for, what problem it solves. No waffle.

## User value
Which persona(s) does this serve? (Casual player / Technical player / Worker)
What does the user do differently because of this feature?
What happens if we don't build it?

## Scope
What is explicitly included in this spec.
What is explicitly excluded (and why, or where it belongs).

## Behaviour
Precise description of how the feature works from the user's perspective.
Reference IA spec page names and component names where relevant.
Cover the happy path, then edge cases, then empty states.

## Technical approach
High-level implementation plan:
- Which modules are touched (mc-web, mc-engine, mc-data, etc.)
- New routes, handlers, pipeline steps required
- Database changes (migrations needed?)
- Skills to load during implementation

## Sub-tasks
Ordered list of implementation tasks. Each should be completable in one focused session.
- [ ] Task 1
- [ ] Task 2
- ...

If more than 5 sub-tasks: flag whether this should be split into multiple specs.

## Acceptance criteria
Concrete, testable. Not "it works" — specific behaviours that can be verified.
- [ ] Criterion 1
- [ ] Criterion 2
- ...

## Out of scope / deferred
Anything raised during spec creation that was deliberately excluded.
Where it belongs (which phase, which future spec).

## Tech lead review
Verdict: [pending]
Notes: [filled by tech-lead agent]
```