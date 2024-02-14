package app.mcorg.project.domain.usecase.project.countedtask;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.project.task.CountedTask;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddCountedTaskUseCase
        extends UseCase<AddCountedTaskUseCase.InputValues, AddCountedTaskUseCase.OutputValues> {

    final GetProjectUseCase getProjectUseCase;
    final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final String taskName = input.name();
        final Priority taskPriority = input.priority();
        final int needed = input.needed();

        Project project = get(projectId);
        CountedTask countedTask = CountedTask.create(taskName, taskPriority, needed);
        project.getTasks().add(countedTask);

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
                              Priority priority,
                              int needed) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}
