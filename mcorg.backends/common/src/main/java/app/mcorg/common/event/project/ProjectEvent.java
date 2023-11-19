package app.mcorg.common.event.project;

import app.mcorg.common.event.DomainEvent;

public sealed interface ProjectEvent extends DomainEvent
        permits ProjectCreated,
        ProjectDeleted,
        ProjectDependencyAddedToTask,
        ProjectNameChanged {
    String id();

    String teamId();
}
