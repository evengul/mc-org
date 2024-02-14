package app.mcorg.project.presentation.rest.entities.project;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.project.ProjectDependency;
import org.springframework.lang.NonNull;

public record ProjectDependencyResponse(@NonNull String projectId,
                                        @NonNull Priority priority,
                                        @NonNull ProjectDependency.Direction direction) {
    public static ProjectDependencyResponse from(ProjectDependency dependency) {
        return new ProjectDependencyResponse(dependency.projectId(), dependency.priority(), dependency.direction());
    }
}
