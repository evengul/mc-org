package app.mcorg.project.domain.usecase.project;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.task.AddTaskUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ChangeProjectUseCase<I extends UseCase.InputValues, O extends UseCase.OutputValues> extends UseCase<I, O> {

    final GetProjectUseCase getProjectUseCase;
    final StoreProjectUseCase storeProjectUseCase;

    protected Project get(String id) {
        return getProjectUseCase
                .execute(new GetProjectUseCase.InputValues(id))
                .project();
    }

    protected AddTaskUseCase.OutputValues store(Project project) {
        Project stored = storeProjectUseCase
                .execute(new StoreProjectUseCase.InputValues(project))
                .project();
        return new AddTaskUseCase.OutputValues(stored);
    }
}