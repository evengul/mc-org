---
name: deliver
description: >
  Finishes an issue after the owner has approved it: waits for green CI,
  rebases only when the recipe says so, squash-merges the PR, follows the
  production deploy, cleans up the worktree and pulls master into the main
  checkout. Run only when the owner asks (/deliver, or "merge when green,
  clean up and pull master"). Merging to master deploys to production.
disable-model-invocation: true
argument-hint: "[PR number]"
---

# Deliver: from approved PR to a cleaned-up worktree

The owner has approved the change and is leaving the session. The job is to
get the PR onto master without breaking production, and to leave the machine
tidy. Don't ask along the way about things this recipe decides. Stop and
report where it says so.

Merging is an outward action, and on master it is a production deploy
(`prod.yml`). Run this only when the owner has asked for it in this session.

## 0. Starting point

- Not in a worktree under `.claude/worktrees/`: stop.
- The owner calling `/deliver` is the approval. Uncommitted changes that
  belong to the issue are committed and pushed per the `commit` skill. What
  doesn't belong (repro scripts, notes, sketches) stays untracked and goes in
  the report. If you are unsure whether a file belongs, stop and ask.
- Find the PR: the argument, otherwise
  `gh pr view --json number,state,baseRefName,url,body` from the worktree
  branch. If there is none, create it per the `commit` skill, review step
  included.
- `baseRefName` is not `master`: this is a stacked PR. Stop. It is rebased
  with `--onto` by hand (`commit` → "Stacked PRs").

## 1. Was it reviewed?

The PR description has a Review section naming the reviewed commit
(`commit` → step 3). Compare:

```bash
git log --oneline <reviewed-sha>..HEAD
```

- Nothing, or only fixes for review findings and CI: carry on.
- New behaviour since the review: run `code-review` on that range only, fix
  what holds up, and update the Review section. A finding that would change
  what the owner approved: stop and report.
- No Review section and the change touches code: run step 3 of `commit` now.
  Docs- or config-only: carry on.

## 2. Does it need a rebase?

Rebase in these three cases, and no others:

- **Conflict.** `gh pr view <n> --json mergeable` says `CONFLICTING`.
- **Migrations on both sides.** The PR adds a migration, and master has new
  ones too:

  ```bash
  git fetch origin master
  git diff --name-only origin/master...HEAD -- webapp/mc-web/src/main/resources/db/migration/
  git diff --name-only HEAD...origin/master -- webapp/mc-web/src/main/resources/db/migration/
  ```

  If both print something, rebase, and make the PR's migration version
  higher than every version on master (rename the file; it hasn't run in
  production yet). Flyway doesn't run out of order here: a migration whose
  version is below the highest one applied fails `flyway:migrate` in the
  deploy job, and production is not updated.
- **Untested source on master.** Nothing after the merge tests the
  combination: `prod.yml` only compiles before it deploys. The PR's CI
  tested the PR merged onto master *as master was when the run started*. So
  if master has gained commits touching `webapp/` since then, those and this
  PR have never been tested together:

  ```bash
  gh run list --branch <branch> --workflow dev.yml --limit 1 --json createdAt,headSha
  git log --oneline --since=<createdAt> origin/master -- webapp/
  ```

  Any output: rebase, so CI tests the combination before it deploys.
  Commits that only touch docs or `.github/` don't count.

The rebase:

```bash
git rebase origin/master
cd webapp && mvn clean compile
git push --force-with-lease origin <branch>
```

If it stops on a conflict, resolve it when it is mechanical (imports,
adjacent lines). If it isn't, `git rebase --abort` and stop.

## 3. Wait for CI

Follow the run, not the PR checks. Find the run for the commit you pushed:

```bash
git rev-parse HEAD
gh run list --branch <branch> --workflow dev.yml --limit 3 --json databaseId,headSha,status
```

The run appears a few seconds after the push. If there's no row with your
`headSha` yet, wait and ask again. Then, in the background:

```bash
gh run watch <run-id> --exit-status --interval 30
```

You are notified when it finishes, so don't poll in a loop. Confirm with
`gh pr checks <n>` that `compile / Compile`, `Unit Tests` and
`Integration Tests` are `pass`.

`gh pr checks <n> --watch` doesn't work right after a push. Before GitHub has
registered checks for the new commit it exits immediately with "no checks
reported", and in the background that looks like CI finished. A watch
started before a force-push follows the old commit.

Red CI:

- Read the failure: `gh run view <run-id> --log-failed`.
- Compile errors are fixed straight away. Commit, push, back to step 3.
- A failing test: find the cause. If the fix is obvious and doesn't change
  behaviour the owner approved, fix it as above. Otherwise stop and report
  the cause and what you propose.
- A failure unrelated to the diff that passes on a re-run is flaky:
  `gh run rerun <run-id> --failed` once, and mention it in the report. If it
  fails again, stop.

## 4. Merge

If master moved while CI ran, run step 2 again before merging. Only its three
cases send you back to a rebase.

```bash
gh pr merge <n> --squash
```

Never `--delete-branch`. The repository deletes the remote branch itself,
and the flag makes gh check out master locally, which fails because master is
checked out in the main checkout. Without the flag gh touches nothing
locally.

```bash
gh pr view <n> --json state,mergeCommit
```

`state` is `MERGED`: carry on. Never run the merge again without checking
this.

## 5. Follow the deploy

`prod.yml` runs on master only when the merge touched one of its paths
(`webapp/*/src/main/**`, the POMs, `Dockerfile`, `fly.toml`). Find the run for
the merge commit:

```bash
gh run list --branch master --workflow prod.yml --limit 3 --json databaseId,headSha,status
```

No run for the merge commit within a minute, and the PR touched none of those
paths: nothing deploys, which is expected. Otherwise
`gh run watch <run-id> --exit-status --interval 30` in the background, and
clean up the worktree meanwhile.

If it goes red, production may be on the old version, or half deployed when
the migration ran and the deploy didn't. Read the failure
(`gh run view <run-id> --log-failed`) and report it to the owner at once, with
which step failed and a proposed fix. The fix is a new issue and a new PR,
not a revert you make on your own.

## 6. Linear

The GitHub integration is best-effort, so this step sets the status itself
rather than checking that the integration did.

- The issue in the title, and any with `Closes MCO-xxx`: `save_issue` with
  `state: "Done"`, then `get_issue` and read `status` back. If the issue has
  no PR attachment, add the PR with `links`. Both are harmless when the
  integration already did them. If the PR was only part of the issue and the
  rest wasn't split out (`commit` → "Linear keywords"), leave it open instead
  and say so.
- An issue with `Refs MCO-xxx` stays where it is. That is intended.
- An issue that was only mentioned, under "Not included" for example, may
  have been moved by the integration. Put it back **only** when its
  `stateHistory` shows the move within a minute of the PR opening or
  merging. A move at any other time is the owner's: leave it, and ask if it
  looks wrong.

*(This said "go to Done by themselves. Check they did" until 2026-10-07.
That day the integration linked and closed #505 and #507, but never touched
#506 or #508; both issues sat In Progress after deploy, and none of the four
ever passed through In Review. The same session then moved MCO-590 back to
Backlog, taking the owner's own move to Todo for the integration's.)*

## 7. Clean up the worktree

1. Stop the dev server this session started (`run.sh`), if it's running. It
   otherwise points into a directory that is about to disappear. Don't touch
   other sessions' servers.
2. List untracked files (`git status --short`). If there's anything the owner
   might want, name it in the report before it is deleted.
3. Reset the branch to the commit the worktree was created from. Its reflog's
   oldest line is the creation ("branch: Created from origin/master"):

   ```bash
   git reflog show --format='%h %gs' <branch> | tail -1
   git reset --hard <that commit>
   ```

   `ExitWorktree` counts commits after that point. After a squash merge
   there are always some, because the squash commit never becomes an
   ancestor of the branch. On the creation point there's nothing to count,
   and the safety check passes without `discard_changes`.
4. `ExitWorktree` with `action: "remove"`. Its hooks prune the worktree's Neon
   branch and its Playwright browser. Gitignored files (`webapp/local.env`,
   keys) don't count. If it refuses on untracked files you already named in
   point 2, `discard_changes: true` is fine. If it refuses on commits,
   something isn't merged. Stop and report; don't override.

   A worktree the session didn't enter with `EnterWorktree` can't be left
   with `ExitWorktree`. Remove it from the main checkout instead:
   `git worktree remove <path>`, `git branch -D <branch>`, then
   `bash webapp/scripts/worktree-db-cleanup.sh --prune` and
   `bash ~/dev/claude-tools/playwright/worktree-playwright.sh --prune`.

Never remove the worktree the session is standing in with anything other
than `ExitWorktree`. If a script or `git worktree remove` deletes the
directory under the session, the shell loses its working directory. To clean
up several worktrees, do it from the main checkout after `ExitWorktree`.

## 8. Pull master

```bash
git pull --ff-only origin master
```

Don't run migrations from the main checkout. Production migrated in the
deploy job, and the main checkout's own database is the owner's to manage.

If `--ff-only` fails, the main checkout has local changes or commits. Don't
touch them; report.

## 9. Report

Short, for an owner who comes back later:

- PR and merge commit
- whether it was rebased, fixed or re-run along the way, and why
- whether a second review ran, and what it found
- whether `prod.yml` ran, and whether it went green
- Linear issues that were moved
- anything unexpected
