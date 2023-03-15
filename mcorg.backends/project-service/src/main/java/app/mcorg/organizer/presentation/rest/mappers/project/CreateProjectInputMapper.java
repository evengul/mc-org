package app.mcorg.organizer.presentation.rest.mappers.project;

import app.mcorg.organizer.domain.usecase.project.CreateProjectUseCase;
import app.mcorg.organizer.presentation.rest.entities.project.CreateProjectRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CreateProjectInputMapper {
    public static CreateProjectUseCase.InputValues map(CreateProjectRequest request) {
        return new CreateProjectUseCase.InputValues(
                request.name()
        );
    }
}
