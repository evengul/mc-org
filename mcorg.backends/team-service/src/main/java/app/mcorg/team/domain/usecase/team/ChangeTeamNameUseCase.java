package app.mcorg.team.domain.usecase.team;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChangeTeamNameUseCase extends UseCase<ChangeTeamNameUseCase.InputValues, ChangeTeamNameUseCase.OutputValues> {

    private final GetTeamUseCase getTeamUseCase;
    private final UnitOfWork<Team> unitOfWork;

    public OutputValues execute(InputValues input) {
        String id = input.id;
        String name = input.name;

        Team team = get(id);
        team.setName(name);

        return new OutputValues(unitOfWork.add(team));
    }

    private Team get(String id) {
        return getTeamUseCase.execute(new GetTeamUseCase.InputValues(
                id
        )).team();
    }

    public record InputValues(String id, String name) implements UseCase.InputValues {
    }

    public record OutputValues(Team team) implements UseCase.OutputValues {
    }
}