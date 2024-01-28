package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.exceptions.NotFoundException;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetProjectUseCase extends UseCase<GetProjectUseCase.InputValues, GetProjectUseCase.OutputValues> {

    private final Projects repository;

    public OutputValues execute(InputValues input) {
        final String id = input.id();

        Project project = repository.get(id)
                .orElseThrow(() -> NotFoundException.project(id));

        return new OutputValues(project);
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}