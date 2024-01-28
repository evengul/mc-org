package app.mcorg.project.presentation.rest.mappers.project;

import app.mcorg.project.domain.usecase.project.CreateProjectUseCase;
import app.mcorg.project.presentation.rest.entities.project.CreateProjectRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CreateProjectInputMapper {
    public static CreateProjectUseCase.InputValues map(CreateProjectRequest request) {
        return new CreateProjectUseCase.InputValues(
                request.name(),
                request.teamId(),
                request.worldId()
        );
    }
}
