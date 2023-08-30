package app.mcorg.common.event.project;

import app.mcorg.common.domain.model.Priority;

import java.util.UUID;

public record ProjectDependencyAddedToTask(String id,
                                           UUID taskId,
                                           Priority priority) implements ProjectEvent {
}
