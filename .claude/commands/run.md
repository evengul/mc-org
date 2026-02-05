Start the application locally. Pass flags through to the script.

Usage: /run [args]

Examples:
- `/run` - Start with local.env (default)
- `/run --env microsoft` - Start with Microsoft auth
- `/run --env test` - Start with test env
- `/run --debug` - Start with remote debug on port 5005
- `/run --debug --suspend` - Start and wait for debugger to attach

Run `webapp/scripts/run.sh $ARGUMENTS`