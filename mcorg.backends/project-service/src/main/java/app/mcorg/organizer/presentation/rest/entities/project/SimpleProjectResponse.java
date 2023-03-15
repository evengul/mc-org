package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.project.CountedTask;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.model.project.Task;
import org.springframework.lang.NonNull;

import static java.util.function.Predicate.not;

public record SimpleProjectResponse(@NonNull String id, @NonNull String name, @NonNull boolean isArchived, @NonNull int totalTasks, @NonNull int incompleteTasks) {
    public static SimpleProjectResponse from(Project project) {
        int totalTasks = project.tasks().size() + project.countedTasks().size();
        int incompleteTasks = (int) project.tasks().stream().filter(not(Task::isDone)).count() +
                (int) project.countedTasks().stream().filter(not(CountedTask::isDone)).count();

        return new SimpleProjectResponse(
                project.id(),
                project.name(),
                project.isArchived(),
                totalTasks,
                incompleteTasks
        );
    }
}
