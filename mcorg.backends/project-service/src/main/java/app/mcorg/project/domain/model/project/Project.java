package app.mcorg.project.domain.model.project;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.project.ProjectCreated;
import app.mcorg.common.event.project.ProjectEvent;
import app.mcorg.common.event.project.ProjectNameChanged;
import app.mcorg.project.domain.model.project.task.CountedTask;
import app.mcorg.project.domain.model.project.task.DoableTask;
import app.mcorg.project.domain.model.project.task.Task;
import app.mcorg.project.domain.model.project.task.Tasks;
import app.mcorg.project.domain.model.schematic.Schematic;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Getter
@AllArgsConstructor
public class Project extends AggregateRoot<ProjectEvent> {
    private final String id;
    private final String teamId;
    private final String worldId;
    private String name;
    private boolean isArchived;
    private final List<ProjectDependency> projectDependencies;
    private final Tasks tasks;
    private final List<String> users;

    public static Project newInstance(List<String> users, String name, String teamId, String worldId) {
        Project project = new Project(ObjectId.get().toHexString(), teamId, worldId, name, false, emptyList(), Tasks.create(), users);
        project.raiseEvent(new ProjectCreated(project.id, project.teamId, project.worldId, project.name, project.users.getFirst()));
        return project;
    }

    public static Project from(List<String> users, String teamId, String worldId, Schematic schematic) {
        List<Task> countedTasks = schematic.amounts().entrySet().stream()
                .map(entry -> CountedTask.create(entry.getKey(), Priority.LOW, entry.getValue()))
                .map(Task.class::cast)
                .toList();

        Project project = new Project(ObjectId.get().toHexString(), teamId, worldId, schematic.name(), false, emptyList(), Tasks.create(countedTasks), users);
        project.raiseEvent(new ProjectCreated(project.id, project.teamId, project.worldId, project.name, project.users.getFirst()));
        return project;
    }

    public void setName(String newName) {
        this.name = newName;
        this.raiseEvent(new ProjectNameChanged(this.id, this.teamId, newName));
    }

    public void addUser(String username) {
        this.users.add(username);
    }

    public void removeUser(String username) {
        this.users.removeIf(existingUser -> existingUser.equals(username));
    }

    public Project archive() {
        this.isArchived = true;
        return this;
    }

    public Project open() {
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
