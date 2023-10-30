package app.mcorg.team.domain.usecase.team;

import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.exceptions.NotFoundException;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetTeamUseCase extends UseCase<GetTeamUseCase.InputValues, GetTeamUseCase.OutputValues> {

    private final Teams teams;

    public OutputValues execute(InputValues input) {
        final String id = input.id;

        Team team = teams.get(id)
                .orElseThrow(() -> NotFoundException.team(id));

        return new OutputValues(team);
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(Team team) implements UseCase.OutputValues {
    }
}