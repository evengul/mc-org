---
name: docs-planning
description: Planning reference for MC-ORG. Load when scoping a feature or epic, validating a technical approach before implementation, decomposing large work into shippable pieces, or deciding whether product/design docs need to be consulted first.
user-invocable: false
---

# Planning Reference

How to validate and decompose work before writing code. Module boundaries,
restricted areas, and the autonomous-agent freedom levels are owned by
CLAUDE.md — this skill covers the planning altitude on top of them.

---

## Before proposing an approach

- CLAUDE.md is the ground truth for module boundaries and critical rules.
  For work touching `mc-engine` or `mc-data`, read those sub-module
  CLAUDE.md files **before** forming an opinion, not during implementation.
- For anything user-visible, load `/docs-ia` (information architecture,
  personas, build phases, progressive disclosure) and `/docs-product`
  (design intent). A plan that conflicts with the IA is wrong even if it is
  technically sound.
- Check the current build phase against docs-ia and recent Linear activity —
  don't assume; phases advance.

## The user-visibility question

Will a user see, feel, or experience anything differently when this is done?

- **Yes** → it's a feature: validate user value, affected personas, and IA
  fit before technical planning. "It's just a rewrite/refactor" doesn't make
  something technical — a frontend rewrite changes everything the user sees.
  Only the user-visible outcome matters, not the motivation.
- **No** (invisible: migrations without UI impact, infra, caching with
  identical behaviour, refactors with identical output) → purely technical
  validation is enough.

## Decomposing large work

Each piece must be:
- **Independently shippable** — the app keeps working after each merge
- **Tightly scoped** — adjacent concerns become their own issues, named and
  set aside explicitly; scope creep is the primary failure mode of large work
- **Sequenced explicitly** — hard dependencies named; everything else
  flagged as parallelisable (separate worktrees, no file conflicts)

Migration strategy for rewrites: page-by-page / component-by-component /
parallel running — the app must stay functional at every intermediate state,
with a rollback position at each stage.

## Restricted-area isolation

Work that touches `PathSuggestionScorer`, `ItemSourceGraph` structure, auth
plugins, or destructive migrations (see CLAUDE.md "Flag before acting")
must be isolated into its own piece — never bundled with other changes —
so the human checkpoint reviews exactly that change and nothing else.

## Plan-quality bar

A plan is ready when it names: modules touched (and which need a
sub-CLAUDE.md read first), new routes/steps/migrations, the skills to load
during implementation, the test tiers the change requires, and what is
explicitly out of scope. Track the work in Linear (`/linear`) — specs live
in the Linear issue, not in files.
