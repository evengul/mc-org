package app.mcorg.project.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.api.UserProvider;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddTeamToWorldUseCase
        extends UseCase<AddTeamToWorldUseCase.InputValues, AddTeamToWorldUseCase.OutputValues> {

    private final UserProvider userProvider;
    private final GetWorldUseCase getWorldUseCase;
    private final UnitOfWork<World> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        World world = get(input.worldId);
        world.addTeam(input.teamId, input.teamName);
        return new OutputValues(unitOfWork.add(world));
    }

    private World get(String id) {
        return getWorldUseCase.execute(new GetWorldUseCase.InputValues(id))
                              .world();
    }

    public record InputValues(String worldId,
                              String teamId,
                              String teamName) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}
