---
name: commit
description: Guided commit workflow for MC-ORG. Compiles, runs tests, checks the pre-commit checklist, stages files, and creates a well-formed commit message.
disable-model-invocation: false
---

# Commit Changes

Follow these steps in order. Stop and report if any step fails.

## Step 1: Compile

Only compile if the changes include Kotlin/Java source files (`webapp/src/main/**`). Skip this step for config-only or non-code changes (e.g. workflow files, docs).

```bash
cd webapp && mvn clean compile
```

Zero errors required. If it fails, fix the errors before continuing.

## Step 2: Run Tests

Only run tests if the changes include Kotlin/Java source files (`webapp/src/main/**`). Skip this step for config-only or non-code changes.

```bash
./webapp/scripts/test.sh
```

Run with `--database` if the changes include `@Tag("database")` tests, `--integration` if they include `*IT.kt` integration tests. Add `--exclude-unit-tests` to skip unit tests when only running a specific tier.

All tests must pass. If any fail, fix them before continuing.

This might not include tests that failed prior to the current session.
If you think a test might have failed before this session, ask me before you
stash and test on main.

## Step 3: Identify Linear Issue

Check if there is a target Linear issue (MCO-xxx) for this commit:
1. If the user mentioned an issue number, use that.
2. If a task spec file is open or referenced, look for a Linear issue ID in it.
3. If the branch name contains an issue ID (e.g. `mco-123-...`), use that.
4. Otherwise, ask the user: "Is there a Linear issue for this commit?"

Store the issue ID (if any) for use in the commit message.

## Step 4: Review Changes

Run `git status` and `git diff` to see what will be staged. Summarize the changes for the commit message.

## Step 5: Pre-Commit Checklist

- [ ] No inline `style =` attributes — use CSS utility classes
- [ ] Authorization in Ktor plugins, not pipelines
- [ ] Correct import: `kotlinx.html.stream.createHTML` (not `kotlinx.html.createHTML`)
- [ ] HTMX targets match response element IDs
- [ ] Tests written for new functionality
- [ ] No hardcoded secrets or user IDs

## Step 6: Stage Files

Stage only the relevant files by name. Avoid `git add -A` unless every change is intentional.

## Step 7: Commit

Write the commit message in this format:

```
<type>: <imperative summary under 72 chars>

<optional body: explain the why, not the what>

MCO-XXX  ← REQUIRED if a Linear issue was identified in Step 3

Co-Authored-By: Claude <current model> 4.6 <noreply@anthropic.com>  ← include if AI-assisted
```

Type follows conventional commits: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, etc.

**Linear issue reference:** If Step 3 identified a Linear issue, you MUST include it in the commit message footer. Use `Closes MCO-XXX` if the commit fully resolves the issue, or `Refs MCO-XXX` if it's partial progress toward the issue.

Examples of good summaries:
- `feat: add project invitation email notification`
- `fix: fix task completion not updating parent progress`
- `refactor: refactor world permission checks into plugin`

Use a HEREDOC for the commit to preserve formatting:

```bash
git commit -m "$(cat <<'EOF'
feat: add project invitation email notification

Send an email when a user is invited to a project.

Closes MCO-123

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```
