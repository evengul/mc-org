---
name: start
description: >
  Starts work on a Linear issue: brings the main checkout up to date, reads
  the issue and checks its premise against the code, creates a worktree named
  after the issue, moves the issue to In Progress and lays out the plan. Use
  when a session opens with just an issue number ("MCO-571", "take MCO-571",
  "/start MCO-571"), or when asked to begin on an issue. The other end is
  `/deliver`.
argument-hint: "MCO-xxx"
---

# Start: from issue number to a plan in a worktree

The owner gives you an issue number and expects you to work out the rest.
The job is to read the issue, check it still holds, and set the work up so
Linear shows someone is on it. Stop and ask only where this recipe says so,
or when the issue needs a decision that is the owner's to make.

## 0. Starting point

The session should be in the main checkout, not in a worktree. Inspect other
worktrees with `git -C <path> …`, never `cd <path> && …`: a `cd` moves the
shell, and `EnterWorktree` then remembers the wrong directory to return to.

## 1. Is the main checkout up to date?

```
git fetch origin master
git rev-list --count HEAD..origin/master
git status --short
```

- master is behind: `git merge --ff-only origin/master` before reading code.
  Otherwise you read a state that no longer exists, and the issue can look
  unsolved because the fix isn't pulled.
- `--ff-only` fails, or `git status` shows changes: another session is
  working on master. Don't touch it. Note which files, and mention it if they
  overlap the issue.

`EnterWorktree` branches from `origin/master` and is safe either way. It is
the reading before it that hits the local checkout.

## 2. Is someone already on it?

```
git worktree list
gh pr list --state open --search "MCO-xxx"
```

A worktree or an open PR carrying the issue number means another session has
it. Stop and ask.

## 3. Read the issue

- `get_issue` with `includeRelations`, and `list_comments`. Read the parent,
  `blockedBy` and the sibling issues in the same project too. A sibling can
  remove exactly what this issue builds on.
- Status Done or Canceled: stop and ask.
- Status In Progress or In Review with no worktree, branch or PR: a PR
  probably only mentioned the issue and Linear moved it. The issue is free.
- Status Done, but the issue describes phases that were never built: a
  phase-1 PR with the number in its title closed it (`commit` → "Linear
  keywords"). Ask whether to reopen it or split the rest out.

## 4. Check the premise against the code

An open issue doesn't prove the work remains, and the description can be
wrong in several ways. Grep for the code the issue is about before planning:

- **Is it done?** Issues written as a report of a change ("the UPDATE block
  is removed") are often a summary of a PR that never moved the issue.
  Partly done is more common than fully done.
- **Does the problem exist?** Follow the chain all the way down: the route's
  auth plugin, the pipeline steps, the SQL. An issue about a permission rule
  can describe a state the role checks never allow.
- **Is the gain real?** Find what the user clicks today and read what it
  sends. A missing table proves something isn't modelled, not that the user
  does it by hand.
- **What already exists?** Before proposing a new model: read the migrations
  under `webapp/mc-web/src/main/resources/db/migration/`, the routes, the
  domain model in `mc-domain`, and the integration tests. A comment saying a
  case is deliberately left alone is a decision, not a detail.
- **Restricted area?** If the issue touches `mc-engine` or `mc-data`, read
  their CLAUDE.md now, and say in the plan which human checkpoint in
  CLAUDE.md ("Graph & Scoring") applies.

If the issue is solved, the premise is wrong, or the gain is different from
what it says: stop and say so, with file and line. Propose closing or
rewriting it. Don't build on a reason you know doesn't hold.

## 5. Worktree

```
EnterWorktree name=mco-xxx-short-name
```

The name is the issue number and two to four words from the title, lower
case with hyphens. Choose it now, so the branch, the PR and the Neon branch
carry the issue number from the start and nothing needs renaming. Wait for
the hooks to finish (Maven repo, keys, Playwright shim, Neon branch,
migrations, demo user; about 90 s on a cold daemon) before running anything.
If something is missing afterwards, the `worktree-infrastructure` skill has
the fixes.

## 6. In Progress

Set the issue to In Progress with `save_issue`, and assign the owner if no
one is. The response can show the old status even when the change took.
Check `updatedAt` before trying again.

## 7. Plan for the owner

Short:

- what the issue asks, in your words
- what the code showed, and anything that differs from the issue
- the plan: which tests are written first, and which files change
- choices the owner has to make, if any

If the issue is small and unambiguous and the plan needs no choice, carry on
without waiting.

## Afterwards

- CLAUDE.md's Working Style and Test Expectations apply.
- Small bugs found along the way are fixed in the same PR, with their own
  commit and a test that reproduces them (CLAUDE.md → "Findings along the
  way").
- Runtime check: `/verify`. Review, commit and PR: `commit`. Merge and
  cleanup: `/deliver`.
- **UI changes: hand the owner a running app.** Once `/verify` passes, start
  the dev server (`run.sh`, on the worktree's own port) and leave it up. Tell
  the owner the URL and the page to open. If the change only shows with data
  the world lacks (a pending farm, a dismissed line), add that data to the
  worktree's Neon branch first and say what you added. Do this before asking
  for the go-ahead to commit: the owner pokes through it, and that is the
  approval. `/deliver` stops the server.
