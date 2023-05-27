package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UnarchiveProjectUseCase extends UseCase<UnarchiveProjectUseCase.InputValues, UnarchiveProjectUseCase.OutputValues> {

    final GetProjectUseCase getProjectUseCase;
    final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String id = input.id();

        Project project = get(id).unarchive();

        return store(project);
    }

    private Project get(String id) {
        return getProjectUseCase
                .execute(new GetProjectUseCase.InputValues(id))
                .project();
    }

    private OutputValues store(Project project) {
        Project stored = storeProjectUseCase
                .execute(new StoreProjectUseCase.InputValues(project))
                .project();
        return new OutputValues(stored);
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}