# mc-pipeline

Generic pipeline framework — `Result`, `Step`, `PipelineScope`, and `MergeSteps`. The railway-oriented programming foundation used by all other modules.

## Purpose

Provides the composable error-handling primitives. Steps process inputs and return `Result<E, S>`. Pipelines chain steps with short-circuit-on-failure semantics.

## Tech

- Kotlin stdlib + coroutines only
- Maven build, JVM 21 target
- Publishes a test JAR (`classifier: tests`) used by mc-nbt and mc-web for test utilities

## Key Types

### `Result<E, S>` (`app.mcorg.pipeline`)
Sealed interface: `Success<S>` or `Failure<E>`. Provides `map`, `flatMap`, `mapError`, `recover`, `fold`, `getOrElse`, `tryCatch`.

### `Step<I, E, S>` (`app.mcorg.domain.pipeline`)
Functional interface: `suspend fun process(input: I): Result<E, S>`. Companion has `value()` and `validate()` factory methods.

### `PipelineScope<E>` (`app.mcorg.domain.pipeline`)
Railway DSL. Use `pipeline(onSuccess, onFailure) { ... }` blocks. Inside, call `.bind()` on Results to extract values or short-circuit. Supports `parallel()` for concurrent step execution (2, 3, or 4-way).

### `MergeSteps` (`app.mcorg.domain.pipeline`)
Combinators: `combine()`, `transform()`, `validate()` for merging parallel results.

## Package Note

`Result` is in `app.mcorg.pipeline`. `Step`, `PipelineScope`, and `MergeSteps` are in `app.mcorg.domain.pipeline`. Watch the imports.

## Build

```bash
cd webapp && mvn compile -pl mc-pipeline
mvn test -pl mc-pipeline
```

## Tests

Located in `src/test/kotlin/app/mcorg/pipeline/`. Covers Result, Step, PipelineScope, and MergeSteps. `TestUtils.kt` is shared via the test JAR.
