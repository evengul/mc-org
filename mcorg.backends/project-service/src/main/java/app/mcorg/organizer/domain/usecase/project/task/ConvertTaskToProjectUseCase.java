package app.mcorg.organizer.domain.usecase.project.task;

import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.usecase.UseCase;
import app.mcorg.organizer.domain.usecase.project.GetProjectUseCase;
import app.mcorg.organizer.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConvertTaskToProjectUseCase extends UseCase<ConvertTaskToProjectUseCase.InputValues, ConvertTaskToProjectUseCase.OutputValues> {

    private final GetProjectUseCase getProjectUseCase;
    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        Project project = get(input.projectId);

        Project created = store(project.fromTask(input.taskId));
        Project withoutOriginalTask = store(project.removeTask(input.taskId));

        return new OutputValues(withoutOriginalTask, created);
    }

    private Project get(String projectId) {
        return getProjectUseCase.execute(new GetProjectUseCase.InputValues(projectId))
                .project();
    }

    private Project store(Project project) {
        return storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project();
    }

    public record InputValues(String projectId, String taskId) implements UseCase.InputValues {
    }

    public record OutputValues(Project initial, Project created) implements UseCase.OutputValues {
    }
}