package app.mcorg.project.domain.usecase.project;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreProjectUseCase extends UseCase<StoreProjectUseCase.InputValues, StoreProjectUseCase.OutputValues> {

    private final UnitOfWork<Project> unitOfWork;

    public OutputValues execute(InputValues input) {
        return new OutputValues(unitOfWork.add(input.project));
    }

    public record InputValues(Project project) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}
