package app.mcorg.project.presentation.rest.entities.project;

import app.mcorg.project.domain.model.project.Project;
import org.springframework.lang.NonNull;

import java.util.List;

public record ProjectListResponse(@NonNull List<SimpleProjectResponse> projects) {
    public static ProjectListResponse from(List<Project> projects) {
        return new ProjectListResponse(
                projects.stream()
                        .map(SimpleProjectResponse::from)
                        .toList()
        );
    }
}
