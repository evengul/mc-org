package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AddVersionUrlToResourceUseCase extends UseCase<AddVersionUrlToResourceUseCase.InputValues, AddVersionUrlToResourceUseCase.OutputValues> {

    private final GetOrThrowResourceUseCase getResourceUseCase;
    private final StoreResourceUseCase storeResourceUseCase;

    public OutputValues execute(InputValues input) {
        final String name = input.name;
        final String version = input.version;
        final String url = input.url;

        Resource resource = getResource(name);
        resource.addVersionUrl(version, url);

        return storeAndReturn(resource);
    }

    private Resource getResource(String name) {
        return getResourceUseCase
                .execute(new GetOrThrowResourceUseCase.InputValues(name))
                .resource();
    }

    private OutputValues storeAndReturn(Resource resource) {
        Resource stored = storeResourceUseCase.execute(new StoreResourceUseCase.InputValues(resource)).resource();
        return new OutputValues(stored);
    }

    public record InputValues(String name, String version, String url) implements UseCase.InputValues {
    }

    public record OutputValues(Resource resource) implements UseCase.OutputValues {
    }
}