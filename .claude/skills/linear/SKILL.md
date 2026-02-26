---
name: linear
description: Create, update, or link Linear issues in the MC-ORG workspace. Use for new features, bugs, and tasks.
disable-model-invocation: false
---

# Linear Issue Management

**Workspace:** evegul | **Team:** Mcorg

Always use MCP Linear tools (`mcp__linear__*`). Never create GitHub issues.

## Creating an Issue

```
mcp__linear__save_issue:
  team: "Mcorg"
  title: "<concise imperative title>"
  description: "<markdown: context, acceptance criteria, relevant file paths>"
  state: "Todo"
  priority: 0–4  (0=None, 1=Urgent, 2=High, 3=Normal, 4=Low)
  labels: [...]  (optional)
```

Good titles:
- `Add email notification for project invitations`
- `Fix task progress not updating when subtask is completed`
- `Refactor world permission checks into Ktor plugin`

Good descriptions include:
- What the feature does or what the bug is
- Acceptance criteria (bullet list)
- Links to relevant files or routes

## Updating an Issue

Pass the `id` field to update rather than create:

```
mcp__linear__save_issue:
  id: "<issue-id>"
  state: "In Progress"
```

## Listing My Issues

```
mcp__linear__list_issues:
  team: "Mcorg"
  assignee: "me"
```

## Linking Issues to PRs / Commits

- In commit messages: `Closes MCO-XXX`
- In PR body: include the Linear issue URL
- After opening a PR: comment the PR URL on the Linear issue

## States Available

Check with `mcp__linear__list_issue_statuses` (team: Mcorg) for the current list. Common states:
- `Todo` — planned but not started
- `In Progress` — actively being worked on
- `In Review` — PR open
- `Done` — merged and deployed
- `Cancelled` — no longer relevant
