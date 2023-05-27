package app.mcorg.project.presentation.rest.entities.project.task;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.task.DoableTask;
import app.mcorg.project.presentation.rest.entities.project.ProjectDependencyResponse;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public record TaskResponse(@NonNull UUID id,
                           @NonNull String name,
                           @NonNull Priority priority,
                           @NonNull boolean isDone,
                           @NonNull List<ProjectDependencyResponse> projectDependencies) {
    public static TaskResponse from(DoableTask doableTask) {
        return new TaskResponse(
                doableTask.getId(),
                doableTask.getName(),
                doableTask.getPriority(),
                doableTask.isDone(),
                doableTask.getProjectDependencies()
                        .stream()
                        .map(ProjectDependencyResponse::from)
                        .toList()
        );
    }
}
