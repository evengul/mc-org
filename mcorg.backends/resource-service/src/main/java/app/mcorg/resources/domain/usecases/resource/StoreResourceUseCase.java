package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreResourceUseCase extends UseCase<StoreResourceUseCase.InputValues, StoreResourceUseCase.OutputValues> {

    private final UnitOfWork<Resource> unitOfWork;

    public OutputValues execute(InputValues input) {
        return new OutputValues(unitOfWork.add(input.resource));
    }

    public record InputValues(Resource resource) implements UseCase.InputValues { }

    public record OutputValues(Resource resource) implements UseCase.OutputValues { }
}