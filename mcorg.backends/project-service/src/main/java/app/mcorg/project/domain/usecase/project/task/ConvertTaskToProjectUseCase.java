package app.mcorg.project.domain.usecase.project.task;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ConvertTaskToProjectUseCase extends UseCase<ConvertTaskToProjectUseCase.InputValues, ConvertTaskToProjectUseCase.OutputValues> {

    private final GetProjectUseCase getProjectUseCase;
    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final UUID taskId = input.taskId();

        Project originalProject = get(projectId);
        Project taskProject = originalProject.getTasks()
                .doableToProject(taskId, originalProject);
        originalProject.getTasks().remove(taskId);

        Project created = store(taskProject);
        Project withoutOriginalTask = store(originalProject);

        return new OutputValues(withoutOriginalTask, created);
    }

    private Project get(String projectId) {
        return getProjectUseCase.execute(new GetProjectUseCase.InputValues(projectId))
                .project();
    }

    private Project store(Project project) {
        return storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project();
    }

    public record InputValues(String projectId, UUID taskId) implements UseCase.InputValues {
    }

    public record OutputValues(Project initial, Project created) implements UseCase.OutputValues {
    }
}