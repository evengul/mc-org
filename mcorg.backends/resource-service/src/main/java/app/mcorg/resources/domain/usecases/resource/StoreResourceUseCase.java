package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.Resources;
import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreResourceUseCase extends UseCase<StoreResourceUseCase.InputValues, StoreResourceUseCase.OutputValues> {

    private final Resources api;

    public OutputValues execute(InputValues input) {
        return new OutputValues(api.persist(input.resourcePack));
    }

    public record InputValues(ResourcePack resourcePack) implements UseCase.InputValues {
    }

    public record OutputValues(ResourcePack resourcePack) implements UseCase.OutputValues {
    }
}