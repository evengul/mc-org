package app.mcorg.project.presentation.rest.entities.project;

import app.mcorg.project.domain.model.project.Project;
import org.springframework.lang.NonNull;

public record SimpleProjectResponse(@NonNull String id, @NonNull String name, @NonNull boolean isArchived,
                                    @NonNull int totalTasks, @NonNull int incompleteTasks) {
    public static SimpleProjectResponse from(Project project) {
        int totalTasks = project.getTasks().size();
        int incompleteTasks = project.getTasks().incomplete();

        return new SimpleProjectResponse(
                project.getId(),
                project.getName(),
                project.isArchived(),
                totalTasks,
                incompleteTasks
        );
    }
}
