package app.mcorg.organizer.domain.usecase.project;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import static app.mcorg.organizer.domain.model.exceptions.I18AbleExceptions.notFound;

@RequiredArgsConstructor
public class GetProjectUseCase extends UseCase<GetProjectUseCase.InputValues, GetProjectUseCase.OutputValues> {

    private final ProjectRepository repository;

    public OutputValues execute(InputValues input) {
        final String id = input.id();

        Project project = repository.get(id)
                .orElseThrow(notFound(String.format("Project %s", id)));

        return new OutputValues(project);
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}