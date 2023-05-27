package app.mcorg.project.domain.model.project.task;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.Project;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static app.mcorg.project.domain.model.exceptions.I18AbleExceptions.notFound;
import static java.util.Collections.emptyList;

public record Tasks(List<Task> tasks) {

    public int size() {
        return tasks.size();
    }

    public static Tasks create(List<Task> tasks) {
        return new Tasks(tasks);
    }

    public static Tasks create() {
        return create(emptyList());
    }

    public Tasks add(Task task) {
        this.tasks.add(task);
        return this;
    }

    public Tasks add(List<Task> tasks) {
        this.tasks.addAll(tasks);
        return this;
    }

    public void remove(UUID id) {
        Task task = get(id);
        this.tasks.remove(task);
    }

    public <T extends Task> T get(UUID id, Class<T> tClass) {
        return this.tasks.stream()
                .filter(tClass::isInstance)
                .map(tClass::cast)
                .filter(task -> task.getId().equals(id))
                .findFirst()
                .orElseThrow(notFound(id.toString()));
    }

    public Task get(UUID id) {
        return get(id, Task.class);
    }

    public void rename(UUID taskId, String name) {
        get(taskId).rename(name);
    }

    public void reprioritize(UUID taskId, Priority priority) {
        get(taskId).reprioritize(priority);
    }

    public void doableDone(UUID taskId) {
        get(taskId, DoableTask.class).done();
    }

    public void doableUndone(UUID taskId) {
        get(taskId, DoableTask.class).undone();
    }

    public Project doableToProject(UUID taskId) {
        return doables()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElseThrow(notFound(taskId.toString()))
                .convertToProject();
    }

    public void countableDoneMore(UUID taskId, int done) {
        get(taskId, CountedTask.class).doneMore(done);
    }

    public void countableNeedsMore(UUID taskId, int needs) {
        get(taskId, CountedTask.class).needsMore(needs);
    }

    public int incomplete() {
        return (int) tasks.stream()
                .filter(task -> !task.isDone())
                .count();
    }

    public void dependsOn(UUID taskId, String projectId, Priority priority) {
        get(taskId)
                .dependsOn(projectId, priority);
    }

    public Stream<DoableTask> doables() {
        return tasks.stream()
                .filter(DoableTask.class::isInstance)
                .map(DoableTask.class::cast);
    }

    public Stream<CountedTask> countables() {
        return tasks.stream()
                .filter(CountedTask.class::isInstance)
                .map(CountedTask.class::cast);
    }
}
