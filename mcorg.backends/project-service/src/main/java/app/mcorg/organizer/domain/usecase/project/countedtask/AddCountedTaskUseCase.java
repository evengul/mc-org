package app.mcorg.organizer.domain.usecase.project.countedtask;

import app.mcorg.organizer.domain.model.project.CountedTask;
import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.usecase.UseCase;
import app.mcorg.organizer.domain.usecase.project.GetProjectUseCase;
import app.mcorg.organizer.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddCountedTaskUseCase extends UseCase<AddCountedTaskUseCase.InputValues, AddCountedTaskUseCase.OutputValues> {

    final GetProjectUseCase getProjectUseCase;
    final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String projectId = input.projectId();
        final String taskName = input.name();
        final Priority taskPriority = input.priority();
        final int needed = input.needed();

        CountedTask countedTask = CountedTask.newInstance(taskName, taskPriority, needed);
        Project project = get(projectId).addCountedTask(countedTask);

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

    public record InputValues(String projectId, String name, Priority priority, int needed) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}