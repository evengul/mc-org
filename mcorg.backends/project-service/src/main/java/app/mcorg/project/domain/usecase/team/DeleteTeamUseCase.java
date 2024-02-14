package app.mcorg.project.domain.usecase.team;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteTeamUseCase extends UseCase<DeleteTeamUseCase.InputValues, DeleteTeamUseCase.OutputValues> {

    private final UnitOfWork<Team> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        unitOfWork.remove(input.id);
        return new OutputValues();
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}
