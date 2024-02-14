package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.exceptions.UnconfirmedException;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class DeleteAllProjectsUseCase
        extends UseCase<DeleteAllProjectsUseCase.InputValues, DeleteAllProjectsUseCase.OutputValues> {

    private final Projects repository;

    public OutputValues execute(InputValues input) {
        boolean confirmed = Optional.ofNullable(input.confirm).orElse(false);

        if (confirmed) {
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
