# mc-domain

Pure domain models and value objects. No business logic, no framework dependencies
(see the serialization exception below).

## Purpose

This module defines the shared vocabulary of the entire application. Every other module depends on it. Changes here ripple everywhere — keep models stable and minimal.

## Tech

- Kotlin stdlib + `kotlinx-serialization-core` only (no Ktor, no database, no logging, no I/O)
- Maven build, JVM 21 target
- Package: `app.mcorg.domain.model.*`

## Allowed dependencies (and the one exception)

The only third-party dependency is `kotlinx-serialization-core`. Domain models **may**
carry `@Serializable` annotations and, where a sealed/value type needs custom wire
handling, a `KSerializer` (e.g. `ResourceSource.SourceType.SourceTypeSerializer`). This
is a deliberate, stated exception: with a single entity layer and railway architecture,
a separate DTO layer purely to keep annotations off the domain would duplicate every
model plus mappers for negligible gain. `serialization-core` is pure-Kotlin, compile-time,
and effectively a marker — categorically lighter than JPA/Jackson.

Everything else stays out: **no logging, no I/O, no Ktor, no database, no other framework
deps.** Models are pure data + lookups. Behaviour that needs runtime context (e.g. logging
an unrecognized id) belongs at the boundary that has that context — for source-type
resolution that is the `mc-data` extraction layer, not `SourceType.of()`, which returns
`UNKNOWN` silently.

## Structure

```
model/
  admin/       — AdminStatistics, ManagedUser, ManagedWorld
  idea/        — Idea, IdeaCategory, Comment, Rate, Dimensions, etc.
  invite/      — Invite, InviteStatus
  minecraft/   — Item, MinecraftId, MinecraftTag, Litematica, MinecraftVersion, ServerData, etc.
  notification/ — Notification
  project/     — Project, ProjectStage, ProjectType, ProjectDependency, ProjectProduction
  resources/   — ResourceMap, ResourceSource, ResourceQuantity, ResourceProducer, etc.
  task/        — ActionTask
  user/        — User, Role
  world/       — World, Roadmap, WorldStatistics
```

## Conventions

- **Data classes** for models, **enums/sealed interfaces** for types
- Value objects are immutable
- No business logic — validation and rules belong in `mc-web` pipeline steps
- `MinecraftId` is the sealed interface for `Item` and `MinecraftTag` — used throughout engine and data modules

## Build

```bash
cd webapp && mvn compile -pl mc-domain
mvn test -pl mc-domain
```

## Tests

Located in `src/test/kotlin/app/mcorg/domain/model/`. Unit tests for value objects and model behavior.
