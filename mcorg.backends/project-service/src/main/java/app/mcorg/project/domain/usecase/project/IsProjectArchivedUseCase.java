package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IsProjectArchivedUseCase extends UseCase<IsProjectArchivedUseCase.InputValues, IsProjectArchivedUseCase.OutputValues> {

    private final Projects repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.isArchived(input.id));
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(boolean isArchived) implements UseCase.OutputValues {
    }
}