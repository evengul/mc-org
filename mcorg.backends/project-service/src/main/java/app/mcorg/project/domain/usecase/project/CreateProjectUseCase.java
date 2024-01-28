package app.mcorg.project.domain.usecase.project;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CreateProjectUseCase extends UseCase<CreateProjectUseCase.InputValues, CreateProjectUseCase.OutputValues> {

    private final StoreProjectUseCase storeProjectUseCase;
    private final UsernameProvider usernameProvider;

    public OutputValues execute(InputValues input) {
        final String name = input.name();
        final String teamId = input.teamId();
        final String worldId = input.worldId();

        Project project = Project.newInstance(List.of(usernameProvider.get()), name, teamId, worldId);

        return persistAndReturn(project);
    }

    private OutputValues persistAndReturn(Project project) {
        return new OutputValues(storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project());
    }

    public record InputValues(String name, String teamId, String worldId) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}