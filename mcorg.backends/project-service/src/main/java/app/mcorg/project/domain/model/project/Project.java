package app.mcorg.project.domain.model.project;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.DomainEvent;
import app.mcorg.project.domain.model.project.task.CountedTask;
import app.mcorg.project.domain.model.project.task.DoableTask;
import app.mcorg.project.domain.model.project.task.Task;
import app.mcorg.project.domain.model.project.task.Tasks;
import app.mcorg.project.domain.model.schematic.Schematic;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Getter
@AllArgsConstructor
public class Project extends AggregateRoot<DomainEvent> {
    private final String id;
    private String name;
    private boolean isArchived;
    private final List<ProjectDependency> projectDependencies;
    private final Tasks tasks;

    public static Project newInstance(String name) {
        return new Project(null, name, false, emptyList(), Tasks.create());
    }

    public static Project from(Schematic schematic) {
        List<Task> countedTasks = schematic.amounts().entrySet().stream()
                .map(entry -> CountedTask.create(entry.getKey(), Priority.LOW, entry.getValue()))
                .map(Task.class::cast)
                .toList();

        return new Project(null, schematic.name(), false, emptyList(), Tasks.create(countedTasks));
    }

    public Project archive() {
        this.isArchived = true;
        return this;
    }

    public Project unarchive() {
        this.isArchived = false;
        return this;
    }

    public void isDependedOnBy(String projectId, Priority priority) {
        this.projectDependencies.add(new ProjectDependency(projectId, priority, ProjectDependency.Direction.IS_DEPENDED_ON_BY));
    }

    public Stream<DoableTask> doableTasks() {
        return this.getTasks().doables();
    }

    public Stream<CountedTask> countedTasks() {
        return this.getTasks().countables();
    }
}
