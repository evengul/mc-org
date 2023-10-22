package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UserProvider;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.world.domain.model.world.World;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateWorldUseCase extends UseCase<CreateWorldUseCase.InputValues, CreateWorldUseCase.OutputValues> {

    private final UserProvider userProvider;
    private final UnitOfWork<World> unitOfWork;

    public OutputValues execute(InputValues input) {
        final String worldName = input.name();

        World world = World.create(worldName, userProvider.get());

        return new OutputValues(unitOfWork.add(world));
    }

    public record InputValues(String name) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}