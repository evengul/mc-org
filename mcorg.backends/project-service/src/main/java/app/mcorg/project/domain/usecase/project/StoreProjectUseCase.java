package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreProjectUseCase extends UseCase<StoreProjectUseCase.InputValues, StoreProjectUseCase.OutputValues> {

    private final Projects repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.persist(input.project));
    }

    public record InputValues(Project project) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}