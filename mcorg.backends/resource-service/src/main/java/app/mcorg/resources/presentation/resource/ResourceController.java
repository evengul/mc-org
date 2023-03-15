package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.domain.usecases.UseCaseExecutor;
import app.mcorg.resources.domain.usecases.resource.GetAllResourcesUseCase;
import app.mcorg.resources.domain.usecases.resource.GetResourcesForVersionUseCase;
import app.mcorg.resources.presentation.entities.ResourceResponse;
import app.mcorg.resources.presentation.entities.ResourcesResponse;
import app.mcorg.resources.presentation.mappers.GetAllResourcesOutputMapper;
import app.mcorg.resources.presentation.mappers.GetResourcesForVersionOutputMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ResourceController implements ResourceResource {
    private final UseCaseExecutor executor;
    private final GetAllResourcesUseCase getAllResourcesUseCase;
    private final GetResourcesForVersionUseCase getResourcesForVersionUseCase;

    @Override
    public CompletableFuture<ResponseEntity<List<ResourceResponse>>> getAll() {
        return executor.execute(
                    getAllResourcesUseCase,
                    new GetAllResourcesUseCase.InputValues(),
                    GetAllResourcesOutputMapper::map
                );
    }

    @Override
    public CompletableFuture<ResponseEntity<ResourcesResponse>> getResourcesForVersion(String version) {
        return executor.execute(
                getResourcesForVersionUseCase,
                new GetResourcesForVersionUseCase.InputValues(version),
                GetResourcesForVersionOutputMapper::map
        );
    }
}
