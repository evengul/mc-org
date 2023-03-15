package app.mcorg.organizer.domain.usecase.project;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IsProjectArchivedUseCase extends UseCase<IsProjectArchivedUseCase.InputValues, IsProjectArchivedUseCase.OutputValues> {

    private final ProjectRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.isArchived(input.id));
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(boolean isArchived) implements UseCase.OutputValues {
    }
}