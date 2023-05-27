package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.Resources;
import app.mcorg.resources.domain.model.exceptions.NotFoundException;
import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetResourceUseCase extends UseCase<GetResourceUseCase.InputValues, GetResourceUseCase.OutputValues> {

    private final Resources repository;

    public OutputValues execute(InputValues input) {
        final String id = input.id();

        ResourcePack pack = repository.getResourcePack(id)
                .orElseThrow(() -> new NotFoundException(String.format("Resource pack [%s] not found", id)));

        return new OutputValues(pack);
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(ResourcePack resourcePack) implements UseCase.OutputValues {
    }
}