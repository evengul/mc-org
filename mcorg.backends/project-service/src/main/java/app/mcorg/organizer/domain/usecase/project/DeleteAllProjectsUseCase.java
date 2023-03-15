package app.mcorg.organizer.domain.usecase.project;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.model.exceptions.UnconfirmedException;
import app.mcorg.organizer.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class DeleteAllProjectsUseCase extends UseCase<DeleteAllProjectsUseCase.InputValues, DeleteAllProjectsUseCase.OutputValues> {

    private final ProjectRepository repository;

    public OutputValues execute(InputValues input) {
        boolean confirmed = Optional.ofNullable(input.confirm).orElse(false);

        if(confirmed) {
            repository.deleteAll();
        } else {
            throw new UnconfirmedException("Projects");
        }

        return new OutputValues();
    }

    public record InputValues(Boolean confirm) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}