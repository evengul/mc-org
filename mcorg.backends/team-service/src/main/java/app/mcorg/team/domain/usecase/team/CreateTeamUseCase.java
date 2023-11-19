package app.mcorg.team.domain.usecase.team;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateTeamUseCase extends UseCase<CreateTeamUseCase.InputValues, CreateTeamUseCase.OutputValues> {

    private final UnitOfWork<Team> unitOfWork;
    private final UsernameProvider usernameProvider;

    public OutputValues execute(InputValues input) {
        final String worldId = input.worldId;
        final String name = input.name;
        final String creator = usernameProvider.get();

        Team team = Team.create(name, creator, worldId);

        return new OutputValues(unitOfWork.add(team));
    }

    public record InputValues(String worldId, String name) implements UseCase.InputValues {
    }

    public record OutputValues(Team team) implements UseCase.OutputValues {
    }
}