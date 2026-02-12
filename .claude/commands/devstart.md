Start the full local development environment in the correct order.

Run each step sequentially, stopping if any step fails:

1. **Docker check**: Run `docker info` to verify Docker is running. If it isn't, run sudo service docker start. If this fails, report the issue and return early.
2. **Start database**: Run `webapp/scripts/start-db.sh`
3. **Wait for database**: Run `docker compose -f webapp/docker-compose-local.yaml exec db pg_isready` in a retry loop (up to 10 seconds) until the database accepts connections.
4. **Run migrations**: Run `webapp/scripts/migrate-locally.sh`
5. **Start application**: Run `webapp/scripts/run.sh $ARGUMENTS`

Report the status of each step as you go. If any step fails, diagnose the issue and suggest a fix rather than continuing.

Usage: /devstart [args]

Examples:
- `/devstart` - Full startup with defaults
- `/devstart --debug` - Full startup with remote debug enabled
- `/devstart --env microsoft` - Full startup with Microsoft auth
