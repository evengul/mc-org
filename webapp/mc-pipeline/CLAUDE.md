# mc-pipeline

Generic pipeline framework — `Result`, `Step`, and `PipelineScope`. The railway-oriented programming
foundation used by mc-data, mc-nbt and mc-web. `mc-engine` has zero coupling to it; keep it that way.

## Purpose

Provides the composable error-handling primitives. Steps process inputs and return `Result<E, S>`.
Pipelines chain steps with short-circuit-on-failure semantics.

## Tech

- Kotlin stdlib + coroutines only
- Maven build, JVM 21 target
- Publishes a test JAR (`classifier: tests`) used by mc-nbt and mc-web for `TestUtils`

## Key Types

Everything is in **one package, `app.mcorg.pipeline`** (it was split across `app.mcorg.pipeline` and
`app.mcorg.domain.pipeline` until MCO-553; the old package no longer exists).

### `Result<E, S>`
Sealed interface: `Success<S>` or `Failure<E>`. Provides `map`, `flatMap`, `mapError`, `recover`,
`fold`, `peek`, `getOrNull`, `errorOrNull`, `getOrThrow`, `tryCatch`, plus the top-level
`getOrElse` used for early returns: `val v = result.getOrElse { return Result.failure(it) }`.

### `Step<I, E, S>`
`fun interface` with one member: `suspend fun process(input: I): Result<E, S>`. It is the unit of
work. A stateless step is a lambda — `Step<String, E, Int> { Result.success(it.length) }` — and
a step that carries constructor state, or that a test wants to name, is a class or object. Both are
the same type and compose identically; `DatabaseSteps.query(...)` and `ValidationSteps.*` in
mc-web return steps built the first way. There are no factory methods on `Step` (`value` and
`validate` had no callers and were deleted in MCO-553, along with `MergeSteps`).

### `PipelineScope<E>`
Railway DSL. `pipeline(onSuccess, onFailure) { ... }` and `pipelineResult { ... }` open a scope;
inside, `.bind()` on a `Result` extracts the value or short-circuits, and `step.run(input)` is
`step.process(input).bind()`. `parallel()` runs 2, 3 or 4 branches concurrently over
`coroutineScope` + `async`: the first failure wins and cancels the other branches.

The short-circuit is a thrown `PipelineFailure`. That is safe by construction, and
`PipelineScopeTest` pins both halves: `bind` is a scope member, so it can only be called lexically
inside a pipeline block (a `Step.process` body cannot throw it), and `PipelineFailure` extends
`Throwable` rather than `Exception` (with no stack trace), so a `catch (e: Exception)` inside the
block cannot swallow it. Do not wrap a `.run()` / `.bind()` in `runCatching` or
`catch (e: Throwable)` — those are the only two things that could.

## Build

```bash
cd webapp && mvn compile -pl mc-pipeline
mvn test -pl mc-pipeline
```

## Tests

Located in `src/test/kotlin/app/mcorg/pipeline/`. Covers `Result`, `Step`, and `PipelineScope`.
`TestUtils.kt` is shared via the test JAR.
