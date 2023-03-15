package app.mcorg.organizer.domain.model.project;

import app.mcorg.organizer.domain.model.schematic.Schematic;

import java.util.List;
import java.util.stream.Stream;

import static app.mcorg.organizer.domain.model.exceptions.I18AbleExceptions.notFound;
import static java.util.Collections.emptyList;

public record Project(String id, String name, boolean isArchived, List<Task> tasks, List<CountedTask> countedTasks) {
    public static Project newInstance(String name) {
        return new Project(null, name, false, emptyList(), emptyList());
    }

    public static Project from(Schematic schematic) {
        List<CountedTask> countedTasks = schematic.amounts().entrySet().stream()
                .map(entry -> CountedTask.newInstance(entry.getKey(), Priority.LOW, entry.getValue()))
                .toList();
        return new Project(null, schematic.name(), false, emptyList(), countedTasks);
    }

    public Project fromTask(String taskId) {
        return this.tasks.stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(notFound(String.format("Task %s in project %s", taskId, this.id)))
                .convertToProject();
    }

    public Project archive() {
        return new Project(
                this.id,
                this.name,
                true,
                this.tasks,
                this.countedTasks
        );
    }

    public Project unarchive() {
        return new Project(
                this.id,
                this.name,
                false,
                this.tasks,
                this.countedTasks
        );
    }

    public Project addTask(Task task) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                Stream.concat(this.tasks.stream(), Stream.of(task)).toList(),
                this.countedTasks
        );
    }

    public Project removeTask(String id) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks.stream()
                        .filter(task -> !task.id().equals(id))
                        .toList(),
                this.countedTasks
        );
    }

    public Project addCountedTask(CountedTask countedTask) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                Stream.concat(this.countedTasks.stream(), Stream.of(countedTask)).toList()
        );
    }

    public Project removeCountedTask(String id) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                this.countedTasks.stream()
                        .filter(task -> !task.id().equals(id))
                        .toList()
        );
    }

    public Project renameTask(String taskId, String newName) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.rename(newName) : task)
                        .toList(),
                this.countedTasks
        );
    }

    public Project renameCountedTask(String taskId, String name) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                this.countedTasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.rename(name) : task)
                        .toList()
        );
    }

    public Project reprioritizeTask(String taskId, Priority priority) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.prioritized(priority) : task)
                        .toList(),
                this.countedTasks
        );
    }

    public Project reprioritizeCountedTask(String taskId, Priority priority) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                this.countedTasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.reprioritize(priority) : task)
                        .toList()
        );
    }

    public Project doTask(String taskId) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.done() : task)
                        .toList(),
                this.countedTasks
        );
    }

    public Project undoTask(String taskId) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.undone() : task)
                        .toList(),
                countedTasks
        );
    }

    public Project countedTaskNeedsMore(String taskId, int needed) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                this.countedTasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.needsMore(needed) : task)
                        .toList()
        );
    }

    public Project countedTaskDoneMore(String taskId, int done) {
        return new Project(
                this.id,
                this.name,
                this.isArchived,
                this.tasks,
                this.countedTasks.stream()
                        .map(task -> task.id().equals(taskId) ? task.doneMore(done) : task)
                        .toList()
        );
    }


}
