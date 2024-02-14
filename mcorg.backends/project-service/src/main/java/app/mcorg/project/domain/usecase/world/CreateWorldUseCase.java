package app.mcorg.project.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.api.UserProvider;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateWorldUseCase extends UseCase<CreateWorldUseCase.InputValues, CreateWorldUseCase.OutputValues> {

    private final UserProvider userProvider;
    private final UnitOfWork<World> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        return new OutputValues(unitOfWork.add(World.create(input.name, userProvider.get())));
    }

    public record InputValues(String name) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}
