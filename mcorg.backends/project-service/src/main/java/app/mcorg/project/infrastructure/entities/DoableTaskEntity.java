package app.mcorg.project.infrastructure.entities;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.ProjectDependency;

import java.util.List;
import java.util.UUID;

public record DoableTaskEntity(UUID id, String name, Priority priority, Boolean isDone,
                               List<ProjectDependency> projectDependencies) {
}
