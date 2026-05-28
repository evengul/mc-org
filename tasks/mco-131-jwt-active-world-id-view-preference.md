---
linear-issue: "MCO-131"
epic: "MCO-128"
status: approved
phase: 1
created: 2026-03-04
---

# JWT activeWorldId + View Preference DB

## Summary

Add `activeWorldId` as a nullable JWT claim on `TokenProfile`, and create the
`user_project_view_preference` table with pipeline steps and an HTMX endpoint
for reading/writing per-project plan/execute preferences. Pure infrastructure —
no user-visible UI changes. Later features (MCO-133, MCO-135, MCO-138, MCO-139)
depend on this foundation.

## User value

**Technical player and casual player:** The app will resume the correct world
on login (MCO-133 depends on `activeWorldId`), and project detail will remember
whether the user left it in plan or execute view (MCO-138 depends on the DB
preference). Neither behaviour is visible until those features land, but getting
the data model right now prevents a breaking migration later.

Self-hosted fonts eliminate the third-party dependency, reducing latency on slow
connections.

## Scope

**In scope:**

1. `TokenProfile.activeWorldId: Int? = null` — domain model change (mc-domain)
2. `CreateTokenStep` — write claim conditionally
3. `ConvertTokenStep` — read nullable claim (no `withClaimPresence`, no `!!`)
4. Test helpers: `TestDataFactory.createTestTokenProfile` signature update
5. Existing token tests — add `activeWorldId` coverage
6. Flyway migration `V2_30_0` — `user_project_view_preference` table
7. Three pipeline steps: `SetActiveWorldStep`, `GetViewPreferenceStep`, `SetViewPreferenceStep`
8. HTMX endpoint: `POST /worlds/:worldId/projects/:projectId/view-preference`
9. Unit and integration tests for all new steps and the endpoint

**Not in scope:**

- Wiring toggle to project detail template (MCO-138/139)
- Root redirect using `activeWorldId` (MCO-133)
- Setting `activeWorldId` on world entry (MCO-135)
- Clearing `activeWorldId` on world switch (MCO-135)

## Behaviour

### JWT — activeWorldId claim

- Claim name: `active_world_id`
- Written in `CreateTokenStep` only when non-null:
  `input.activeWorldId?.let { withClaim("active_world_id", it) }`
- Read in `ConvertTokenStep`: `jwt.getClaim("active_world_id").asInt()` — returns
  `null` when absent (auth0 java-jwt `NullClaim` behaviour). Let platform type
  flow to `Int?` naturally — do NOT use `!!`.
- NOT added to `withClaimPresence()`. Old tokens decode with `activeWorldId = null`.

### Database — user_project_view_preference

```sql
CREATE TABLE user_project_view_preference (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    view_preference VARCHAR(10) NOT NULL CHECK (view_preference IN ('plan', 'execute')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, project_id)
);
```

Default when no row exists: `execute` (in application code, not DB default row — rows
written only on first explicit toggle).

### Pipeline steps

**`SetActiveWorldStep`** (`pipeline/auth/commonsteps/`)
- Input: `SetActiveWorldInput(profile: TokenProfile, worldId: Int)`
- Output: `String` (new JWT token)
- Process: `CreateTokenStep.process(profile.copy(activeWorldId = worldId))`
- Note: Returns token string only. Cookie replacement happens in the consuming handler
  (MCO-133/MCO-135). This step ships without a consumer endpoint — that is expected.

**`GetViewPreferenceStep`** (`pipeline/project/commonsteps/`)
- Input: `GetViewPreferenceInput(userId: Int, projectId: Int)`
- Output: `String` ("plan" or "execute")
- Process: SELECT from table. If no row found, return `"execute"` (not a failure).

**`SetViewPreferenceStep`** (`pipeline/project/commonsteps/`)
- Input: `SetViewPreferenceInput(userId: Int, projectId: Int, preference: String)`
- Output: `Unit`
- Process: Validate preference is "plan" or "execute", then
  `INSERT ... ON CONFLICT (user_id, project_id) DO UPDATE SET view_preference = EXCLUDED.view_preference, updated_at = now()`
- Use `SafeSQL.insert()`.

### HTMX endpoint

`POST /worlds/:worldId/projects/:projectId/view-preference`

- Route block must install: `AuthPlugin`, `WorldParamPlugin`, then nest `ProjectParamPlugin`
  (order matters — ProjectParamPlugin calls `getWorldId()` internally)
- Form body: `preference=plan` or `preference=execute`
- Handler reads userId from `getUser().id`, projectId from `getProjectId()`, preference from form
- Calls `SetViewPreferenceStep`
- On success: `respondEmptyHtml()` (200 OK, empty body — codebase convention for empty responses).
  Client uses `hx-swap="none"`.
- On invalid preference: `respondBadRequest()` with error fragment
- Registered on new IA URL scheme. Not reachable until MCO-132 lands — accepted.

## Technical approach

### Modules touched

| Module | Change |
|--------|--------|
| `mc-domain` | `TokenProfile` — add `activeWorldId: Int? = null` as last param |
| `mc-web` | Token steps, new steps, handler, router, migration, tests |

No restricted areas touched. Compile from repo root (`mvn clean compile`), not just mc-web.

### Skills to load

`/docs-development`, `/add-endpoint`, `/add-migration`, `/add-step`, `/docs-testing`

### Key files

| File | Action |
|------|--------|
| `mc-domain/.../User.kt` | Add `activeWorldId: Int? = null` to `TokenProfile` |
| `mc-web/.../CreateTokenStep.kt` | Add conditional claim write |
| `mc-web/.../ConvertTokenStep.kt` | Read nullable claim |
| `mc-web/.../TestDataFactory.kt` | Add `activeWorldId` param |
| `mc-web/.../CreateTokenStepTest.kt` | Add tests for claim present/absent |
| `mc-web/.../ConvertTokenStepTest.kt` | Add test for old token decoding |
| `mc-web/src/main/resources/db/migration/V2_30_0__create_user_project_view_preference.sql` | New |
| `mc-web/.../SetActiveWorldStep.kt` | New step |
| `mc-web/.../GetViewPreferenceStep.kt` | New step |
| `mc-web/.../SetViewPreferenceStep.kt` | New step |
| Handler + router files | New endpoint |

## Sub-tasks

- [ ] Add `activeWorldId: Int? = null` to `TokenProfile`. Compile from root.
- [ ] Update `CreateTokenStep` — conditional claim write. Add tests (claim present + absent).
- [ ] Update `ConvertTokenStep` — read nullable claim. Add test (old token without claim decodes OK).
- [ ] Update `TestDataFactory.createTestTokenProfile` — add `activeWorldId` param.
- [ ] Write migration `V2_30_0`. Run `migrate-locally.sh` to verify.
- [ ] Implement `SetActiveWorldStep` with unit test.
- [ ] Implement `GetViewPreferenceStep` with unit tests (success + missing row default).
- [ ] Implement `SetViewPreferenceStep` with unit tests (success + invalid preference + upsert).
- [ ] Implement handler + register endpoint. Write integration test (success, invalid preference).
- [ ] Run `mvn clean compile` and `mvn test` from root. All green.

## Acceptance criteria

- [ ] `TokenProfile` compiles with `activeWorldId: Int? = null` as last param. No existing call sites broken.
- [ ] JWT with `activeWorldId = 42` contains `active_world_id: 42` claim
- [ ] JWT with `activeWorldId = null` contains no `active_world_id` claim
- [ ] JWT without `active_world_id` decodes to `TokenProfile` with `activeWorldId = null` — no error
- [ ] `V2_30_0` migration applies cleanly. Table exists with PK `(user_id, project_id)` and CHECK constraint.
- [ ] FKs reference `users(id)` and `projects(id)` with ON DELETE CASCADE
- [ ] `GetViewPreferenceStep` returns `"execute"` when no row exists
- [ ] `SetViewPreferenceStep` upserts correctly — second call overwrites, no duplicate key error
- [ ] `POST /worlds/:worldId/projects/:projectId/view-preference` with `preference=plan` returns 200 empty HTML
- [ ] `POST` with `preference=invalid` returns 400
- [ ] Route block includes AuthPlugin, WorldParamPlugin, ProjectParamPlugin
- [ ] `mvn clean compile` passes with zero errors from repo root
- [ ] `mvn test` passes with all tests
- [ ] Unit tests cover: token claim presence/absence, old token backward compatibility, step success/failure paths
- [ ] Integration test covers: endpoint success and validation failure

## Out of scope / deferred

| Item | Where it belongs |
|------|-----------------|
| Wiring toggle to project detail template | MCO-138 / MCO-139 |
| Root redirect using `activeWorldId` | MCO-133 |
| Setting `activeWorldId` on world entry | MCO-135 |
| Clearing `activeWorldId` on world switch | MCO-135 |
| Session-level global plan/execute on project list | MCO-136 |
| Worker role default view preference | Phase 3 |

## Tech lead review

Verdict: Changes recommended → incorporated
Notes: (1) Use `respondEmptyHtml()` not 204 — codebase convention. (2) Route must install
WorldParamPlugin then nest ProjectParamPlugin (order matters). (3) Auth plugin required on route.
(4) `SetActiveWorldStep` ships without a consumer endpoint — accepted, MCO-133/135 will consume it.
(5) Don't use `!!` on `getClaim().asInt()` — let platform type flow to `Int?`. (6) `Claim.asInt()`
null behavior confirmed correct but must be tested.
