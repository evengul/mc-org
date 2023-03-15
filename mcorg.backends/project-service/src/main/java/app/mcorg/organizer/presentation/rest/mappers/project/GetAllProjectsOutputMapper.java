package app.mcorg.organizer.presentation.rest.mappers.project;

import app.mcorg.organizer.domain.usecase.project.GetAllProjectsUseCase;
import app.mcorg.organizer.presentation.rest.entities.project.ProjectListResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;

@UtilityClass
public class GetAllProjectsOutputMapper {
    public static ResponseEntity<ProjectListResponse> map(GetAllProjectsUseCase.OutputValues outputValues) {
        return ResponseEntity.ok(ProjectListResponse.from(outputValues.projects()));
    }
}
