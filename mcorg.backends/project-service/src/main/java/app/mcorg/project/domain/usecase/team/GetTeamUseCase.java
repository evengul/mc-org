package app.mcorg.project.domain.usecase.team;

import app.mcorg.project.domain.api.Teams;
import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetTeamUseCase extends UseCase<GetTeamUseCase.InputValues, GetTeamUseCase.OutputValues> {

    private final Teams teams;

    @Override
    public OutputValues execute(InputValues input) {
        return new OutputValues(teams.get(input.id));
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(Team team) implements UseCase.OutputValues {
    }
}
