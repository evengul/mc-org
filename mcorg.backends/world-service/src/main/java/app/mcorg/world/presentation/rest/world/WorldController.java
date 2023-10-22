package app.mcorg.world.presentation.rest.world;

import app.mcorg.common.domain.usecase.UseCaseExecutor;
import app.mcorg.world.domain.usecase.world.ChangeWorldNameUseCase;
import app.mcorg.world.domain.usecase.world.CreateWorldUseCase;
import app.mcorg.world.domain.usecase.world.DeleteWorldUseCase;
import app.mcorg.world.domain.usecase.world.GetWorldUseCase;
import app.mcorg.world.presentation.rest.entities.world.WorldNameChangeRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class WorldController implements WorldResource {
    private final UseCaseExecutor executor;
    private final ChangeWorldNameUseCase changeWorldNameUseCase;
    private final CreateWorldUseCase createWorldUseCase;
    private final DeleteWorldUseCase deleteWorldUseCase;
    private final GetWorldUseCase getWorldUseCase;

    @Override
    public CompletableFuture<ResponseEntity<Void>> createWorld(WorldRequest request) {
        return executor.execute(
                createWorldUseCase,
                new CreateWorldUseCase.InputValues(request.name()),
                outputValues -> ResponseEntity.ok(null)
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> deleteWorld(String id) {
        return executor.execute(
                deleteWorldUseCase,
                new DeleteWorldUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(null)
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> changeWorldName(String id, WorldNameChangeRequest request) {
        return executor.execute(
                changeWorldNameUseCase,
                new ChangeWorldNameUseCase.InputValues(id, request.name()),
                outputValues -> ResponseEntity.ok(null)
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<WorldResponse>> getWorld(String id) {
        return executor.execute(
                getWorldUseCase,
                new GetWorldUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(WorldResponse.from(outputValues.world()))
        );
    }
}
