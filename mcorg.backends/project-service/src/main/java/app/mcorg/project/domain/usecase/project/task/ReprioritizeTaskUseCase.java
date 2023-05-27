package app.mcorg.project.domain.usecase.project.task;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ReprioritizeTaskUseCase extends UseCase<ReprioritizeTaskUseCase.InputValues, ReprioritizeTaskUseCase.OutputValues> {

    final GetProjectUseCase getProjectUseCase;
    final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final UUID taskId = input.taskId();
        final Priority priority = input.priority();

        Project project = get(projectId);
        project.getTasks().reprioritize(taskId, priority);

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

    public record InputValues(String projectId, UUID taskId, Priority priority) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}