package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddResourceUseCase extends UseCase<AddResourceUseCase.InputValues, AddResourceUseCase.OutputValues> {

    private final GetResourceUseCase getResourceUseCase;
    private final StoreResourceUseCase storeResourceUseCase;

    @Override
    public OutputValues execute(InputValues input) {
        final String resourcePackId = input.resourcePackId();
        final String name = input.name();
        final Resource.Type type = input.type();
        final String url = input.url();

        ResourcePack resourcePack = getResource(resourcePackId);

        resourcePack.addResource(name, type, url);

        return storeAndReturn(resourcePack);
    }

    public ResourcePack getResource(String name) {
        return getResourceUseCase.execute(new GetResourceUseCase.InputValues(name))
                .resourcePack();
    }

    public OutputValues storeAndReturn(ResourcePack resource) {
        ResourcePack stored = storeResourceUseCase
                .execute(new StoreResourceUseCase.InputValues(resource))
                .resourcePack();
        return new OutputValues(stored);
    }

    public record InputValues(String resourcePackId, String name, Resource.Type type,
                              String url) implements UseCase.InputValues {
    }

    public record OutputValues(ResourcePack mod) implements UseCase.OutputValues {
    }
}
