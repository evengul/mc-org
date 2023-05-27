package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateProjectUseCase extends UseCase<CreateProjectUseCase.InputValues, CreateProjectUseCase.OutputValues> {

    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String name = input.name();

        Project project = Project.newInstance(name);

        return persistAndReturn(project);
    }

    private OutputValues persistAndReturn(Project project) {
        return new OutputValues(storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project());
    }

    public record InputValues(String name) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}