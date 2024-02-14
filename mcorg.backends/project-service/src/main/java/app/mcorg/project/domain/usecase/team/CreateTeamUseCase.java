package app.mcorg.project.domain.usecase.team;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.api.UserProvider;
import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.world.GetWorldUseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateTeamUseCase extends UseCase<CreateTeamUseCase.InputValues, CreateTeamUseCase.OutputValues> {

    private final UserProvider userProvider;
    private final GetWorldUseCase getWorldUseCase;
    private final UnitOfWork<Team> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        World world = getWorld(input.worldId);
        Team team = Team.create(input.teamName, world.toSlim(), userProvider.get());
        return new OutputValues(unitOfWork.add(team));
    }

    private World getWorld(String id) {
        return getWorldUseCase.execute(new GetWorldUseCase.InputValues(id)).world();
    }

    public record InputValues(String worldId,
                              String teamName) implements UseCase.InputValues {
    }

    public record OutputValues(Team team) implements UseCase.OutputValues {
    }
}
