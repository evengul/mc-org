package app.mcorg.organizer.domain.usecase.project;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreProjectUseCase extends UseCase<StoreProjectUseCase.InputValues, StoreProjectUseCase.OutputValues> {

    private final ProjectRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.persist(input.project));
    }

    public record InputValues(Project project) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}