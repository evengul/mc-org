package app.mcorg.project.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RenameWorldUseCase extends UseCase<RenameWorldUseCase.InputValues, RenameWorldUseCase.OutputValues> {

    private final GetWorldUseCase getWorldUseCase;
    private final UnitOfWork<World> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        final World world = get(input.id);
        world.setName(input.name);
        return new OutputValues(unitOfWork.add(world));
    }

    private World get(String id) {
        return getWorldUseCase.execute(new GetWorldUseCase.InputValues(id))
                              .world();
    }

    public record InputValues(String id,
                              String name) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}
