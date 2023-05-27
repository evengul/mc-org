package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.usecases.UseCaseExecutor;
import app.mcorg.resources.domain.usecases.resource.*;
import app.mcorg.resources.presentation.entities.ResourcePackRequest;
import app.mcorg.resources.presentation.entities.ResourcePackResponse;
import app.mcorg.resources.presentation.entities.ResourcePacksResponse;
import app.mcorg.resources.presentation.entities.ResourceRequest;
import app.mcorg.resources.presentation.mappers.CreateResourcePackOutputMapper;
import app.mcorg.resources.presentation.mappers.GetAllResourcesOutputMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ResourceController implements ResourceResource {
    private final UseCaseExecutor executor;
    private final GetResourceUseCase getResourceUseCase;
    private final GetAllResourcesUseCase getAllResourcesUseCase;
    private final GetResourcePacksInVersion getResourcePacksInVersion;
    private final StoreResourceUseCase storeResourceUseCase;
    private final AddResourceUseCase addResourceUseCase;

    @Override
    public CompletableFuture<ResponseEntity<ResourcePackResponse>> getResourcepack(String id) {
        return executor.execute(
                getResourceUseCase,
                new GetResourceUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(ResourcePackResponse.from(outputValues.resourcePack()))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<ResourcePacksResponse>> getAll() {
        return executor.execute(
                getAllResourcesUseCase,
                new GetAllResourcesUseCase.InputValues(),
                GetAllResourcesOutputMapper::map
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<ResourcePacksResponse>> getResourcesForVersion(String version) {
        return executor.execute(
                getResourcePacksInVersion,
                new GetResourcePacksInVersion.InputValues(version),
                outputValues -> ResponseEntity.ok(new ResourcePacksResponse(outputValues.resourcePacks().stream().map(ResourcePackResponse::from).toList()))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> create(HttpServletRequest httpServletRequest, ResourcePackRequest request) {
        return executor.execute(
                storeResourceUseCase,
                new StoreResourceUseCase.InputValues(ResourcePack.create(request.name(), request.version(), request.type())),
                outputValues -> CreateResourcePackOutputMapper.mapOut(httpServletRequest, outputValues)
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> addVersionedUrl(String packId, ResourceRequest request) {
        return executor.execute(
                addResourceUseCase,
                new AddResourceUseCase.InputValues(packId, request.name(), request.type(), request.url()),
                outputValues -> ResponseEntity.ok().build()
        );
    }
}
