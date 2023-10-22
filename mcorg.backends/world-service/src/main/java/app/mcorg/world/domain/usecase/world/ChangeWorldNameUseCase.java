package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.world.domain.model.world.World;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChangeWorldNameUseCase extends UseCase<ChangeWorldNameUseCase.InputValues, ChangeWorldNameUseCase.OutputValues> {

    private final GetWorldUseCase getWorldUseCase;
    private final UnitOfWork<World> unitOfWork;

    public OutputValues execute(InputValues input) {
        World world = get(input.id);

        world.setName(input.name);

        return new OutputValues(unitOfWork.add(world));
    }

    private World get(String id) {
        return getWorldUseCase.execute(new GetWorldUseCase.InputValues(id)).world();
    }

    public record InputValues(String id, String name) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}