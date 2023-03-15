package app.mcorg.organizer.domain.usecase.project.task;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.model.project.Task;
import app.mcorg.organizer.domain.usecase.UseCase;
import app.mcorg.organizer.domain.usecase.project.GetProjectUseCase;
import app.mcorg.organizer.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddTaskUseCase extends UseCase<AddTaskUseCase.InputValues, AddTaskUseCase.OutputValues> {

    private final GetProjectUseCase getProjectUseCase;
    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final String taskName = input.name();
        final Priority taskPriority = input.priority();

        Task task = Task.newInstance(taskName, taskPriority);
        Project project = get(projectId).addTask(task);

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

    public record InputValues(String projectId, String name, Priority priority) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}