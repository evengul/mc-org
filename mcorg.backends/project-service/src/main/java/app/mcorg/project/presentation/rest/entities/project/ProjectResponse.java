package app.mcorg.project.presentation.rest.entities.project;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.presentation.rest.entities.project.task.CountedTaskResponse;
import app.mcorg.project.presentation.rest.entities.project.task.TaskResponse;
import org.springframework.lang.NonNull;

import java.util.List;

public record ProjectResponse(@NonNull String id,
                              @NonNull String name,
                              @NonNull boolean isArchived,
                              @NonNull List<TaskResponse> tasks,
                              @NonNull List<CountedTaskResponse> countedTasks,
                              @NonNull List<ProjectDependencyResponse> projectDependencies) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.isArchived(),
                project.doableTasks().map(TaskResponse::from).toList(),
                project.countedTasks().sorted().map(CountedTaskResponse::from).toList(),
                project.getProjectDependencies().stream()
                        .map(ProjectDependencyResponse::from)
                        .toList()
        );
    }
}
