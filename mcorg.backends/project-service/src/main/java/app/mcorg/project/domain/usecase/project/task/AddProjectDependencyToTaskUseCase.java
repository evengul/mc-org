package app.mcorg.project.domain.usecase.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.ChangeProjectUseCase;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;

import java.util.UUID;

public class AddProjectDependencyToTaskUseCase extends
                                               ChangeProjectUseCase<AddProjectDependencyToTaskUseCase.InputValues, AddProjectDependencyToTaskUseCase.OutputValues> {

    public AddProjectDependencyToTaskUseCase(GetProjectUseCase getProjectUseCase,
                                             StoreProjectUseCase storeProjectUseCase) {
        super(getProjectUseCase, storeProjectUseCase);
    }

    public OutputValues execute(InputValues input) {
        final String projectIdOfTask = input.projectIdOfTask();
        final UUID taskId = input.taskId();
        final String dependencyProjectId = input.dependencyProjectId();
        final Priority priority = input.priority();

        Project projectWithTask = get(projectIdOfTask);
        Project dependency = get(dependencyProjectId);
        projectWithTask.taskDependsOn(taskId, projectWithTask.getTeamId(), dependencyProjectId, priority);

        store(projectWithTask);
        store(dependency);

        return new OutputValues();
    }

    public record InputValues(String projectIdOfTask,
                              UUID taskId,
                              String dependencyProjectId,
                              Priority priority) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}
