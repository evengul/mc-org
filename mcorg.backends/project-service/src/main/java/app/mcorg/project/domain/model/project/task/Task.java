package app.mcorg.project.domain.model.project.task;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.ProjectDependency;
import lombok.Getter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
public abstract class Task {
    private final UUID id;
    private String name;
    private Priority priority;
    private final List<ProjectDependency> projectDependencies;

    public Task(UUID id, String name, Priority priority, List<ProjectDependency> dependencies) {
        this.id = id;
        this.name = name;
        this.priority = Optional.ofNullable(priority).orElse(Priority.NONE);
        this.projectDependencies = dependencies;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void reprioritize(Priority priority) {
        this.priority = priority;
    }

    public void dependsOn(String projectId, Priority priority) {
        this.projectDependencies.add(new ProjectDependency(projectId, Optional.ofNullable(priority).orElse(Priority.LOW), ProjectDependency.Direction.DEPENDS_ON));
    }

    public abstract boolean isDone();
}
