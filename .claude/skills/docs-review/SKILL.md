---
name: docs-review
description: Code-review checklist for MC-ORG. Load when reviewing a diff, PR, or completed implementation — project-specific checks for pattern compliance, pipeline/SafeSQL usage, HTMX correctness, test coverage, and restricted-area flagging.
user-invocable: false
---

# Review Checklist

Project-specific checks for reviewing MC-ORG changes. The critical rules and
test expectations themselves are owned by CLAUDE.md ("Critical Rules",
"Test Expectations", "Before Committing") — verify against those lists.
This skill adds the pattern-level checks a generic review misses.

---

## Pattern compliance

- Pipeline steps implement `Step<I, E, S>` correctly; business logic returns
  `Result.success/failure` — no exceptions for business-rule failures
- Handlers use `handlePipeline` (or `pipelineResult`), not raw try/catch
- All DB access goes through `DatabaseSteps.query/update/transaction` with
  `SafeSQL` factory methods — flag any constructor use or string interpolation
- Validation steps collect **all** errors before returning, not fail-fast
- External HTTP calls go through `ApiProvider` config objects, never a raw client
- Anything that doesn't match an existing codebase pattern is flagged
  explicitly — new patterns need a stated reason, not silent introduction

## HTMX / template correctness

- Response fragment `id` matches the request's `hx-target` selector exactly
- OOB swaps of `<tr>`/`<td>`/`<tbody>` elements are `<template>`-wrapped
  (see docs-htmx)
- URLs built with the `Link` interface, not hand-assembled strings
- Component classes come from the dsl catalogue (docs-frontend) — no ad-hoc
  classes, no inline styles

## Migrations

- Naming: `V{n}__{description}.sql`, next free number (check the directory,
  not memory)
- Dropped columns/tables require explicit human approval — blocking otherwise

## Test coverage

Check the diff against CLAUDE.md's Test Expectations table. Specifically:
- New endpoint → IT covering success, validation failure, **and** auth failure
- New pipeline step → unit test per distinct failure case, not just the happy path
- Test helpers used correctly: `WithUser` for auth, `TestDataFactory` for
  fixtures, `@Tag("database")` + `DatabaseTestExtension` for DB tests
- Run tiers via `./webapp/scripts/test.sh` (never bare `mvn test` — it
  silently skips database-tagged ITs)

## Restricted areas — flag regardless of code quality

Changes to `PathSuggestionScorer`, `ItemSourceGraph` structure (new edge or
node types), auth plugins, or destructive migrations must be flagged for
human review even if the implementation looks perfect. State what changed
and why a human checkpoint is required.

## Review output

Be specific: file, line or function, what's wrong, what it should be.
"Line 42: uses `SafeSQL(...)` constructor, must use `SafeSQL.select(...)`"
is a review; "this looks fine" is not. Separate blocking issues (rule or
pattern violations, missing tests) from non-blocking polish.
