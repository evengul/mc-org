package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteProjectUseCase extends UseCase<DeleteProjectUseCase.InputValues, DeleteProjectUseCase.OutputValues> {

    private final Projects projects;

    public OutputValues execute(InputValues input) {
        final String id = input.id();

        projects.delete(id);

        return new OutputValues();
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}