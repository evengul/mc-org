---
name: docs-business
description: Business rules and domain workflows for MC-ORG. Use when implementing business logic, checking role permissions, understanding project stage transitions, invitation workflow, task rules, or cascade effects.
user-invocable: false
---

# Business Rules Reference

Domain rules, workflows, and constraints for MC-ORG.

---

## Business Rules Quick Reference

| Rule | Where Enforced |
|------|----------------|
| No circular project dependencies | `GetAvailableProjectDependenciesStep` (recursive SQL excludes cycle-forming candidates from the picker) |
| Only ADMIN+ can invite users | `WorldAdminPlugin` |
| Demo users are read-only in production | `DemoUserPlugin` |
| BANNED users have no world access | `BannedPlugin` |
| Task assignment requires world membership | Validation step |
| No cross-world dependencies | Validation step |
| All worlds private by default | Invitation-only |
| Stage transitions are audited | `project_stage_changes` table |
| Global admins bypass world membership checks | `AuthPlugin` |
| idea_id → SET NULL on idea delete (not cascade) | DB schema |

---

## Role Hierarchy

| Role | Level | Capabilities |
|------|-------|-------------|
| OWNER | 0 | Full control, can delete world, cannot transfer |
| ADMIN | 10 | Invite users (Member/Admin roles), manage settings, edit/delete any project |
| MEMBER | 100 | Create/edit own projects, create/complete tasks, view all content |
| BANNED | 1000 | No access, can be re-invited |

Lower level number = higher authority. Check: `role.isHigherThanOrEqualTo(Role.ADMIN)`

---

## Project Lifecycle (Stages and States)

Stage — where the project is in its build journey (ordered):

```
IDEA → DESIGN → PLANNING → RESOURCE_GATHERING → BUILDING → TESTING → COMPLETED
```

- Can skip stages (e.g., IDEA → COMPLETED for simple projects)
- Can move backwards (e.g., BUILDING → PLANNING for redesigns)
- All transitions logged in `project_stage_changes` table
- Manual transitions by MEMBER+

State — lifecycle/activity status, orthogonal to stage:

```
PENDING / ACTIVE / PAUSED / DONE / CANCELLED / ARCHIVED
```

- Transitions are restricted — `ProjectState.allowedTransitions()` is the state
  machine (e.g. ACTIVE → PAUSED/DONE/CANCELLED; ARCHIVED → PENDING only)
- DONE, CANCELLED, ARCHIVED are terminal (`isTerminal`)

---

## Task Rules

**ItemTask** (material collection):
- Has `List<ItemRequirement>` — each with `itemId`, `quantityRequired`, `quantityDone`
- "Complete" when all item requirements have `quantityDone >= quantityRequired`
- Example: "Collect 64 stacks of stone"

**ActionTask** (work step):
- Binary `completed: Boolean`
- Example: "Place foundation blocks"

Both have: `priority (CRITICAL/NORMAL/NICE_TO_HAVE)`, `assignedTo`, audit fields.

---

## Dependency Rules

- Projects can depend on other projects within the same world only
- No circular dependencies — prevented at selection time: `GetAvailableProjectDependenciesStep`
  excludes any project already in the dependency chain (or reverse chain) via recursive SQL,
  so cycle-forming options are never offered
- Deleting a project removes all its dependency records (both as dependent and as dependency)

---

## Invitation Workflow

```
PENDING → ACCEPTED → Creates world_member entry, sends notification to inviter
        → DECLINED → Sends rejection notification
        → CANCELLED → Inviter cancelled before response
```

- Only ADMIN+ can send invitations
- One invitation per user per world at a time
- Role assigned at invitation time (MEMBER or ADMIN only — not OWNER)
- If user is already BANNED: allow re-invite to restore access

---

## Notification Types

| Type | Trigger |
|------|---------|
| `INVITE_RECEIVED` | Invitation sent to user |
| `INVITE_ACCEPTED` | Invitee accepted |
| `INVITE_DECLINED` | Invitee declined |
| `PROJECT_COMPLETED` | Project moved to COMPLETE stage |
| `TASK_ASSIGNED` | Task assigned to user |
| `DEPENDENCY_READY` | Blocking dependency completed (planned) |
| `ROLE_CHANGED` | User's world role changed |

Notifications persist even if related entity is deleted. Read/unread state tracked per user.

---

## What Happens When...

**Project is deleted:**
- All item_tasks + action_tasks deleted (CASCADE)
- All project_dependencies records deleted
- All project_locations + stage_changes deleted
- idea_id link on other projects: unaffected
- Cannot be undone (no soft delete)

**Idea is deleted:**
- Projects with `idea_id` → set to NULL (ON DELETE SET NULL)
- Projects remain intact

**User removed from world:**
- `world_members` record deleted → immediate access loss
- Task assignments removed
- Projects/tasks created by user remain (audit trail preserved)
- User receives `ROLE_CHANGED` notification

**Circular dependency attempt:**
- Not possible through the UI — the dependency picker only lists projects returned by
  `GetAvailableProjectDependenciesStep`, which excludes cycle-forming candidates in SQL

**Non-member tries to access world:**
- `WorldParamPlugin` checks `world_members` table
- Returns 403 Forbidden
- Exception: global admins (`isSuperAdmin`) bypass this check

---

## Ideas System

- Ideas are **global** (not world-specific)
- Projects can reference their source idea via `ideaId`
- Importing an idea creates a new project with idea data pre-filled
- Category determines which custom `categoryData` fields are available (JSONB)
- Categories (`IdeaCategory`): BUILD, FARM, STORAGE, CART_TECH, TNT, SLIMESTONE, OTHER
- Difficulty (`IdeaDifficulty`): START_OF_GAME, MID_GAME, END_GAME,
  TECHNICAL_UNDERSTANDING_RECOMMENDED, TECHNICAL_UNDERSTANDING_REQUIRED
