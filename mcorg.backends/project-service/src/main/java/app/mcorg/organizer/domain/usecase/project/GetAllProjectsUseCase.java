package app.mcorg.organizer.domain.usecase.project;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetAllProjectsUseCase extends UseCase<GetAllProjectsUseCase.InputValues, GetAllProjectsUseCase.OutputValues> {

    private final ProjectRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.get());
    }

    public record InputValues() implements UseCase.InputValues {
    }

    public record OutputValues(List<Project> projects) implements UseCase.OutputValues {
    }
}