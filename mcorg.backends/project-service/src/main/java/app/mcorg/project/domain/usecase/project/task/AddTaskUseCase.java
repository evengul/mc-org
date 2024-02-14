package app.mcorg.project.domain.usecase.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.project.task.DoableTask;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddTaskUseCase extends UseCase<AddTaskUseCase.InputValues, AddTaskUseCase.OutputValues> {

    private final GetProjectUseCase getProjectUseCase;
    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final String taskName = input.name();
        final Priority taskPriority = input.priority();

        Project project = get(projectId);
        DoableTask doableTask = DoableTask.newInstance(taskName, taskPriority);
        project.getTasks().add(doableTask);

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

    public record InputValues(String projectId,
                              String name,
                              Priority priority) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}
