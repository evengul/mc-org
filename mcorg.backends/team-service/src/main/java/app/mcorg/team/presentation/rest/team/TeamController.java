package app.mcorg.team.presentation.rest.team;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.usecase.UseCaseExecutor;
import app.mcorg.team.domain.usecase.team.ChangeTeamNameUseCase;
import app.mcorg.team.domain.usecase.team.CreateTeamUseCase;
import app.mcorg.team.domain.usecase.team.DeleteTeamUseCase;
import app.mcorg.team.domain.usecase.team.GetTeamUseCase;
import app.mcorg.team.presentation.rest.common.aspect.HasTeamAccess;
import app.mcorg.team.presentation.rest.common.aspect.HasWorldAccess;
import app.mcorg.team.presentation.rest.entities.team.TeamCreatedResponse;
import app.mcorg.team.presentation.rest.entities.team.TeamNameChangeRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class TeamController implements TeamResource {
    private final UseCaseExecutor executor;
    private final ChangeTeamNameUseCase changeTeamNameUseCase;
    private final CreateTeamUseCase createTeamUseCase;
    private final DeleteTeamUseCase deleteTeamUseCase;
    private final GetTeamUseCase getTeamUseCase;

    @Override
    @HasWorldAccess(authority = Authority.PARTICIPANT)
    public CompletableFuture<ResponseEntity<TeamCreatedResponse>> createTeam(String worldId, TeamRequest request) {
        return executor.execute(
                createTeamUseCase,
                new CreateTeamUseCase.InputValues(worldId, request.name()),
                outputValues -> ResponseEntity.status(HttpStatus.CREATED).body(new TeamCreatedResponse(outputValues.team().getWorldId(), outputValues.team().getId()))
        );
    }

    @Override
    @HasTeamAccess(authority = Authority.OWNER)
    public CompletableFuture<ResponseEntity<Void>> deleteTeam(String worldId, String id) {
        return executor.execute(
                deleteTeamUseCase,
                new DeleteTeamUseCase.InputValues(id),
                _ -> ResponseEntity.ok().build()
        );
    }

    @Override
    @HasTeamAccess(authority = Authority.ADMIN)
    public CompletableFuture<ResponseEntity<Void>> changeTeamName(String worldId, String id, TeamNameChangeRequest request) {
        return executor.execute(
                changeTeamNameUseCase,
                new ChangeTeamNameUseCase.InputValues(id, request.name()),
                _ -> ResponseEntity.ok().build()
        );
    }

    @Override
    @HasTeamAccess(authority = Authority.PARTICIPANT)
    public CompletableFuture<ResponseEntity<TeamResponse>> getTeam(String worldId, String id) {
        return executor.execute(
                getTeamUseCase,
                new GetTeamUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(TeamResponse.from(outputValues.team()))
        );
    }
}
