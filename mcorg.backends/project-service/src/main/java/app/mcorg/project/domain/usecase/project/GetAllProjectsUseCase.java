package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetAllProjectsUseCase extends UseCase<GetAllProjectsUseCase.InputValues, GetAllProjectsUseCase.OutputValues> {

    private final Projects repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.get());
    }

    public record InputValues() implements UseCase.InputValues {
    }

    public record OutputValues(List<Project> projects) implements UseCase.OutputValues {
    }
}