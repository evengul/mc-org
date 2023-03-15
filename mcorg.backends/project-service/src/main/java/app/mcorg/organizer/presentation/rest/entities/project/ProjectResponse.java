package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.project.Project;
import org.springframework.lang.NonNull;

import java.util.List;

public record ProjectResponse(@NonNull String id,
                              @NonNull String name,
                              @NonNull boolean isArchived,
                              @NonNull List<TaskResponse> tasks,
                              @NonNull List<CountedTaskResponse> countedTasks) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.id(),
                project.name(),
                project.isArchived(),
                project.tasks().stream()
                        .map(TaskResponse::from)
                        .toList(),
                project.countedTasks().stream()
                        .map(CountedTaskResponse::from)
                        .toList()
        );
    }
}
