---
name: commit
description: >
  Commit, review and PR workflow for Seam (mc-org): compile and test, review
  the branch with a subagent before the owner sees it, commit, push, and open
  the PR with the title and description format master's history is made of.
  Use when committing, pushing, or opening a PR. The format is here, so you
  don't need to read earlier commits or PRs to find the style. Triggers on:
  "commit", "open a PR", "make a PR", "push", "PR description".
---

# Commit, review and PR

Follow the steps in order. Stop and report if one fails.

## How master's history is made

master is squash-merged, and the squash commit takes the **PR title and
nothing else** (the repository's squash message is blank). So:

- **The PR title is the line in `git log`.** Write it for someone reading
  `git log --oneline` in six months.
- **The PR description is the rest of the history.** It is what a reader
  finds when they follow `(#n)` from the log. Write it as the record, not as
  a note to a reviewer.
- Branch commit messages are working notes. They vanish on merge, so keep
  them short and accurate, and don't spend effort on them.

## 1. Compile and test

Only for changes to Kotlin/Java sources (`webapp/*/src/**`). Skip for
config- or docs-only changes.

```bash
cd webapp && mvn clean compile
./webapp/scripts/test.sh            # add --database for mc-web routes, handlers, DB access
```

Zero errors, all green. Note the test counts per tier; the PR description
needs them (CLAUDE.md → "Green is not proof"). A failure that might predate
this session: ask before stashing and testing on master.

## 2. Pre-commit checklist

- [ ] No inline `style =` attributes; CSS utility classes
- [ ] Authorization in Ktor plugins, not pipelines
- [ ] `kotlinx.html.stream.createHTML`, not `kotlinx.html.createHTML`
- [ ] HTMX targets match response element IDs
- [ ] Tests written for new functionality (CLAUDE.md → Test Expectations)
- [ ] No hardcoded secrets or user IDs
- [ ] Changes with a runtime surface were checked with `/verify`
- [ ] `mc-engine` cost/graph changes flagged to the owner (CLAUDE.md → "Graph & Scoring")

## 3. Review by subagent

Before the PR is opened and before the owner is asked to look at it. A
finding here costs a commit; the same finding after the owner approved and
CI went green costs a push, a CI run and a second approval.

1. Run the `code-review` skill on the branch against `origin/master`.
   Level `medium` by default. Use `high` when the diff touches an auth
   plugin, a migration, `webhook/` or `EventEnvelope` (another repo's input),
   or `mc-engine`.
2. UI changes: also run the `ux-reviewer` agent against the running app.
3. Fix what holds up, each as its own commit, with a test where the finding
   is a bug. A finding you dismiss gets one line on why. Don't argue with a
   finding in code comments.
4. Note the reviewed commit (`git rev-parse --short HEAD`) for the PR's
   Review section. `/deliver` uses it to see whether anything landed after
   the review.

Skip the review for docs-only and config-only changes, and say so in the PR.

## 4. Commit

Stage files by name. Avoid `git add -A` unless every change is intentional.

```
MCO-xxx: <what is different for the user, lower-case start>

Co-Authored-By: <the exact line the harness gives for the running model>
```

Write a body only when the commit does something the diff doesn't explain.
Without an issue: `chore: …` for tooling, docs and config.

## 5. Push

```bash
git push -u origin HEAD
```

`git push` can return before the remote is updated. Check with
`git ls-remote --heads origin <branch>` before pushing again.

The repository deletes the remote branch on merge. If you keep working on
the same branch afterwards, `origin/<branch>` points at a commit that no
longer exists, `git status` says "ahead N, behind 1", and
`--force-with-lease` is refused with `stale info`. That is not a divergence:
`git remote prune origin` and push as normal. Never force-push past it.

## 6. The PR

Write the description to a file in the scratchpad and use `--body-file`.
Add `--label preview` at creation time if the owner should click through a
Fly preview (CLAUDE.md → "CI / PR Previews"); adding it later costs a second
CI run.

```bash
gh pr create --base master --title "MCO-xxx: …" --body-file <scratchpad>/pr.md
```

### Title

`MCO-xxx: <English sentence, lower-case start>`

- Say what is different for whoever uses it, not which files changed:
  "every final project gets a panel, and the graph names the states it hid",
  not "add RoadmapPanel component".
- Work split over several PRs: one issue per PR (see "Linear keywords").
- About 72 characters. GitHub appends `(#n)`.
- No issue: `chore: …`.

### Description

```
[MCO-xxx](https://linear.app/evegul/issue/MCO-xxx): <one or two sentences:
what the user gets, and where the work came from (parent issue, design frame)>

## Why

<The problem as the player sees it. Concrete numbers or a concrete case if
you have one. Omit when the opening line already says it.>

## What changed

<Per part of the change: a bold lead-in, then what it does and why it ended
up this way. Constraints as a list. Files and functions in backticks.>

## Verified

<What you saw in the running app (/verify), against which fixture or world.
A table when there are several cases. Cost, if it changed.>

## Tests

- <What each new test pins down. Say so if you mutated the code and saw the
  test fail.>
- Unit tier <N>, database tier <M>, all green. <Migrations: "No other open
  PR adds a V2_xx migration.">

## Review

`code-review` at <level> on <sha>: <N> findings, <fixed / dismissed and why>.
<ux-reviewer result, if UI.>

## Changed meaning

<Optional. Behaviour a user or another surface could notice that isn't the
point of the PR.>

## Not included

<Optional. What a reader might expect, and where it lives instead.>

Closes MCO-xxx

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

Give the counts, not just "green". A tier that silently skipped its tests
also reports success.

## Linear keywords

**The issue number in the PR title can link the PR, and close the issue on
merge.** "Can": the GitHub integration fires on some PRs and not others, so
`/deliver` sets the final status itself (step 6). Don't rely on it either
way. When it does fire, it closes whatever the title names: MCO-566 went to
Done with its phase-1 PR (#487) while two phases were still left, and they
were split out as MCO-571 and MCO-572 afterwards.

So when a PR is only part of an issue, split the remaining phases into their
own issues before merging, or keep the number out of the title and write
`Refs MCO-xxx` in the description.

In the description, the keyword before the issue number decides:

- `Closes MCO-xxx`: the PR finishes the issue. It goes to Done.
- `Refs MCO-xxx`: linked, but left where it is.

An issue number with no keyword can move that issue too, also under "Not
included". Describe what's left out without the number where you can. If it
has to be there, use `Refs`, and after merge check the issue is where it was
(`/deliver` does).

## Stacked PRs

A PR that builds on another is created with `gh pr create --base
<base-branch>` and shows only its own diff. When the base merges, GitHub
retargets it to master.

The base's squash commit is a new SHA on master, so the base's commits in
the follow-up are no longer ancestors, and the diff suddenly shows both. A
plain rebase replays the base's commits and conflicts. Move only what lies
above the old base:

```bash
git rebase --onto origin/master <old-base-tip>
cd webapp && mvn clean compile
git push --force-with-lease origin <branch>
```

- Write down the base tips before you start merging. They are hard to find
  afterwards.
- Check with `gh pr view <n> --json additions,deletions`. If the numbers
  cover the whole stack, the base is wrong.
