package app.mcorg.team.domain.usecase.team;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteTeamUseCase extends UseCase<DeleteTeamUseCase.InputValues, DeleteTeamUseCase.OutputValues> {

    private final UnitOfWork<Team> unitOfWork;

    public OutputValues execute(InputValues input) {
        unitOfWork.remove(input.id);
        return new OutputValues();
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}