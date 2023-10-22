package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.world.domain.model.world.World;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteWorldUseCase extends UseCase<DeleteWorldUseCase.InputValues, DeleteWorldUseCase.OutputValues> {

    private final UnitOfWork<World> unitOfWork;

    public OutputValues execute(InputValues input) {
        unitOfWork.remove(input.id);
        return new OutputValues();
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}