package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.Resources;
import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetAllResourcesUseCase extends UseCase<GetAllResourcesUseCase.InputValues, GetAllResourcesUseCase.OutputValues> {

    private final Resources repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.getResourcePacks());
    }

    public record InputValues() implements UseCase.InputValues {
    }

    public record OutputValues(List<ResourcePack> resourcePacks) implements UseCase.OutputValues {
    }
}