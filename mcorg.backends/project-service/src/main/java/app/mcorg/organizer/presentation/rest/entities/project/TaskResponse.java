package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.domain.model.project.Task;
import org.springframework.lang.NonNull;

public record TaskResponse(@NonNull String id,
                           @NonNull String name,
                           @NonNull Priority priority,
                           @NonNull boolean isDone) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.id(),
                task.name(),
                task.priority(),
                task.isDone()
        );
    }
}
