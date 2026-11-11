---
name: cloud-session
description: >-
  How a Seam/mc-org Claude Code cloud session (claude.ai/code, CLAUDE_CODE_REMOTE=true) is set
  up and how to fix it when it isn't. Load at the start of any cloud session's work; when
  `java -version` says 21 or Maven fails on `release 25`; when Docker is down, `service docker
  start` does nothing, or the DB on localhost:5432 refuses connections; when the planner,
  `/verify` or `cost-diagnostics` needs Minecraft data (cloud-setup.sh --ingest,
  INGEST_VERSIONS); when screenshots of the running app are needed without playwright-cli; or
  when a skill says `gh`, `EnterWorktree`, `neonctl` or `playwright-cli` and the session has
  none of them.
user-invocable: false
---

# Cloud sessions

A cloud session is not a worktree. The container is the isolation, the session's assigned
`claude/*` branch is the feature branch (and the only one it may push), and the database is a
throwaway localhost Docker postgres. None of `worktree-infrastructure` applies.

## What sets the container up

`.claude/hooks/cloud-session-start.sh` runs on every SessionStart when
`CLAUDE_CODE_REMOTE=true`. It writes `JAVA_HOME=/opt/jdk25` to `$CLAUDE_ENV_FILE`, then runs
`webapp/scripts/cloud-setup.sh`:

| Step | Skipped when |
|------|--------------|
| Temurin JDK 25 into `/opt/jdk25` (the image ships 21; `pom.xml` needs 25) | `/opt/jdk25/bin/java` exists |
| `mvn install -DskipTests` (warms `~/.m2`; MCO-285) | stamp in `~/.m2` matches the poms, module list and script |
| `local.env` from `local.env.example`, `DB_PASSWORD=supersecret` | `local.env` exists |
| `dockerd`, then `mcorg-db` (`start-db.sh`) | Docker answers / the container runs |
| `migrate-locally.sh` | applied version set equals `db/migration/` |
| `--ingest`: `ingest-locally.sh` | only with the flag; the ledger makes a repeat a no-op |
| `--seed`: `--ingest`, then `exec:java@seed-demo` (`cli/SeedDemo.kt`) | only with the flag; an existing "Demo World" is left alone |

Cold: about 2 minutes (the `ready in Ns` line says how long it took). Warm: under a second. The script also runs fine by hand, and it does not
install your source changes: `run.sh`, `ingest-locally.sh` and `test.sh` (`-am`) do that, as
they do locally.

The hook is read from the checkout's `.claude/settings.json` when the session starts. A branch
that changes it only gets the new hook in the next session.

## Minecraft data

A fresh DB has none. Ingest before anything that reads it (planner, resources, roadmap,
`/verify` on those pages, `cost-diagnostics`):

```bash
webapp/scripts/cloud-setup.sh --ingest                         # INGEST_VERSIONS=latest: ~1 min
INGEST_VERSIONS=1.21.8,26.2 webapp/scripts/cloud-setup.sh --ingest
```

`INGEST_VERSIONS` takes `latest`, a comma list, or `all`. A version that is not available fails
the run rather than ingesting less. Ingest more than `latest` when the work is about how
versions differ: `mc-data` extraction, a world on an older version, or a cost question where
the source type only exists in some versions (no 1.x version has villager trades, MCO-524).
Run it in the background while you read code; it is 7 s per version plus the build.

## When something is missing

| Symptom | Cause | Fix |
|---------|-------|-----|
| `java -version` says 21, or Maven fails on `release 25` | the hook did not run, or this shell predates it | `.claude/hooks/cloud-session-start.sh`, then `export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH` |
| `Cannot connect to the Docker daemon` | a resume restarts processes but keeps the filesystem | `webapp/scripts/cloud-setup.sh` |
| `service docker start` returns but no daemon | its init script cannot raise ulimit in the sandbox | don't use it; the script starts `dockerd` directly |
| dockerd refuses with "pid file found" | a stale `/var/run/docker.pid` | the script removes it when the pid is not `dockerd` |
| Docker fails anyway | | `/var/log/dockerd-cloud-setup.log` |
| Any other step fails | | the script names the step; its log is `/tmp/seam-cloud-setup.log`, one dated section per run (the SessionStart run is the first) |

## Looking at the app

The owner cannot open the container's localhost. Show them screenshots, or put the `preview`
label on the PR for a Fly preview.

```bash
./webapp/scripts/run.sh > /tmp/seam-run.log 2>&1 &     # port 8080
node webapp/scripts/screenshot.js /worlds <scratchpad>/shots/worlds            # 375 + 1440
node webapp/scripts/screenshot.js /worlds/<id> <scratchpad>/shots/world 375,768,1440
```

`screenshot.js` signs in through the demo sign-in. It prints status, final URL and title per
width; exit 2 means a 4xx/5xx or a page that bounced to sign-in, exit 3 that the demo sign-in
itself failed (`DEMO_USER` unset). Read the images yourself before sending them with
`SendUserFile`. Interactions need their own short Node script: `require('playwright')` works
from any directory, and the browser is preinstalled. Call `report_preview_port` with 8080 once
the app is up.

The demo user starts with no worlds. `webapp/scripts/cloud-setup.sh --seed` gives it one (run it
before `run.sh`: the seed writes to the DB directly and a running server keeps its caches):

| In "Demo World" | Shows on |
|-----------------|----------|
| Iron Farm: COMPLETED/DONE, produces iron ingots | roadmap "Producing", SUPPLIED iron lines |
| Storage Hall: RESOURCE_GATHERING/ACTIVE, chests, hoppers, comparators | planner with crafted lines |
| Sugar Cane Farm: PLANNING, observers, pistons | a second final project |
| Main Base: DESIGN, 3,000 stone bricks, 8,000 planks, 2,000 glass | "Worth a farm", open questions (sand) |
| Nether Hub: IDEA, nothing in it | roadmap "Not in any chain" |
| Storage Hall → Main Base, by hand | roadmap manual ordering |
| Ideas: Stone Generator, Oak Tree Farm, Sand Duper (public) | "Worth a farm" designs, the idea bank |

The world's id is printed (`seed-demo: created 'Demo World' (id 1, …)`); on a fresh DB it is 1,
and Main Base is project 4. Anything a page needs beyond that: create it through the app, or with
`psql -h localhost -U postgres` (`PGPASSWORD=supersecret`; the DB is disposable), and say what
you added. The dataset is `SEED_PROJECTS` / `SEED_IDEAS` in `cli/SeedDemo.kt`.

## Tools that are not here

| Local | Cloud |
|-------|-------|
| `EnterWorktree`, `worktree-*.sh`, Neon branch | nothing; the container is the worktree |
| `gh pr …`, `gh run …` | `mcp__github__*`: `create_pull_request`, `pull_request_read`, `actions_list`, `get_job_logs`, `merge_pull_request` |
| `gh pr list --search` | `gh api -X GET search/issues -f q='repo:evengul/mc-org is:pr …'` (search is all `gh` does here) |
| `gh run watch`, polling | `subscribe_pr_activity`; `send_later` for a later check |
| `playwright-cli` | `screenshot.js`, or a Node Playwright script |
| `neonctl`, `flyctl` | not installed and not needed |

The `start`, `commit`, `verify` and `deliver` skills each have a "Cloud session" section that
maps their own steps.
