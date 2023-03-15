package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddResourceUseCase extends UseCase<AddResourceUseCase.InputValues, AddResourceUseCase.OutputValues> {

    private final GetResourceUseCase getResourceUseCase;
    private final StoreResourceUseCase storeResourceUseCase;

    @Override
    public OutputValues execute(InputValues input) {
        final String name = input.name;
        final Resource.Type type = input.type;
        final String version = input.version;
        final String url = input.url;

        Resource resource = getResource(name, type);
        resource.addVersionUrl(version, url);

        return storeAndReturn(resource);
    }

    public Resource getResource(String name, Resource.Type type) {
        return getResourceUseCase.execute(new GetResourceUseCase.InputValues(name))
                .resource()
                .orElse(Resource.newInstance(name, type));
    }

    public OutputValues storeAndReturn(Resource resource) {
        Resource stored = storeResourceUseCase.execute(new StoreResourceUseCase.InputValues(resource)).resource();
        return new OutputValues(stored);
    }

    public record InputValues(String name, Resource.Type type, String version, String url) implements UseCase.InputValues {}

    public record OutputValues(Resource mod) implements UseCase.OutputValues {}
}
