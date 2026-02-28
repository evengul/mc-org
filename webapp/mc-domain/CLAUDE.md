# mc-domain

Pure domain models and value objects. No business logic, no framework dependencies.

## Purpose

This module defines the shared vocabulary of the entire application. Every other module depends on it. Changes here ripple everywhere — keep models stable and minimal.

## Tech

- Kotlin stdlib only (no Ktor, no database, no serialization annotations)
- Maven build, JVM 21 target
- Package: `app.mcorg.domain.model.*`

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
