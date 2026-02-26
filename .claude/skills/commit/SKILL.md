---
name: commit
description: Guided commit workflow for MC-ORG. Compiles, runs tests, checks the pre-commit checklist, stages files, and creates a well-formed commit message.
disable-model-invocation: false
---

# Commit Changes

Follow these steps in order. Stop and report if any step fails.

## Step 1: Compile

```bash
cd webapp && mvn clean compile
```

Zero errors required. If it fails, fix the errors before continuing.

## Step 2: Run Tests

```bash
cd webapp && mvn test
```

All tests must pass. If any fail, fix them before continuing. 

This might not include tests that failed prior to the current session. 
If you think a test might have failed before this session, ask me before you 
stash and test on main.

## Step 3: Review Changes

Run `git status` and `git diff` to see what will be staged. Summarize the changes for the commit message.

## Step 4: Pre-Commit Checklist

- [ ] No inline `style =` attributes — use CSS utility classes
- [ ] Authorization in Ktor plugins, not pipelines
- [ ] Correct import: `kotlinx.html.stream.createHTML` (not `kotlinx.html.createHTML`)
- [ ] HTMX targets match response element IDs
- [ ] Tests written for new functionality
- [ ] No hardcoded secrets or user IDs

## Step 5: Stage Files

Stage only the relevant files by name. Avoid `git add -A` unless every change is intentional.

## Step 6: Commit

Write the commit message in this format:

```
<operation following convential commits standard><imperative summary under 72 chars>

<optional body: explain the why, not the what>

Closes MCO-XXX  ← include if this resolves a Linear issue

Co-Authored-By: Claude <current model> 4.6 <noreply@anthropic.com>  ← include if AI-assisted
```

Examples of good summaries:
- `feat: Add project invitation email notification`
- `fix: Fix task completion not updating parent progress`
- `refactor: Refactor world permission checks into plugin`

Use a HEREDOC for the commit to preserve formatting:

```bash
git commit -m "$(cat <<'EOF'
Summary line here

Body here (optional).

Closes MCO-123

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```
